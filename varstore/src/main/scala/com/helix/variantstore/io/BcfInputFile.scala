// © 2016 Helix OpCo LLC. All rights reserved.
// Initial Author: Chris Williams

package com.helix.variantstore.io

import java.io.{Closeable, IOException}
import java.net.URLDecoder

import com.helix.variantstore.{Call, Variant, cache}
import com.typesafe.scalalogging.LazyLogging
import htslib.LibhtsLibrary._
import htslib.{bcf1_t, bcf_hdr_t, bcf_info_t}
import org.bridj.Pointer

import scala.collection.mutable
import scala.collection.immutable
import scala.util.Try

/** Extract information from a variant file by wrapping native calls from the htslib library
  *
  * @param filePath absolute path of input file
  * @param callSet default value of the call set, needed to be consistent with GA4GH schema
  */
class BcfInputFile(filePath: String, callSet: String) extends Closeable {
  val NUM_ATTEMPTS: Int = 3
  val BACKOFF_IN_SECONDS: Int = 5

  private val dataFile = BcfInputFileOpener(NUM_ATTEMPTS, BACKOFF_IN_SECONDS).open(filePath)
  private val headerPtr = bcf_hdr_read(dataFile)
  if (headerPtr == Pointer.NULL) throw new IOException("Failed to read header")
  val header: bcf_hdr_t = headerPtr.get
  val contigMap: Map[Int, String] = BcfInputFile.contigMap(header)

  val genotypeCache: mutable.Map[Int, String] = cache[Int, String] {
    id => header.id.get(BCF_DT_ID).get(id).key.getCString
  }
  val filterCache: mutable.Map[Int, String] = cache[Int, String] {
    id => header.id.get(BCF_HL_FLT).get(id).key.getCString
  }

  private val recordPtr = bcf_init()
  val record: bcf1_t = recordPtr.get

  override def close(): Unit = hts_close(dataFile)

  case class Position(start: Int, end: Int, contig: String) {
    lazy val variant: Try[Variant] = Try {
      if (bcf_unpack(recordPtr, BCF_UN_ALL) < 0) {
        throw new IOException(s"Could not unpack record at position ${this}")
      }
      val alleles = record.d.allele.getPointersAtOffset(0, record.n_allele, classOf[Byte]).map {
        p => p.getCString
      }.toList
      Variant(
        start = start,
        end = end,
        referenceName = contig,
        referenceBases = alleles.head,
        alternateBases = alleles.tail,
        calls = Seq(call)
      )
    }

    private lazy val call: Call = {
      val genotype = BcfInputFile.genotype(record, genotypeCache)
      val info = mutable.Map() ++ BcfInputFile.additionalGenotypeData(record, genotypeCache)
      info += "FILTER" -> BcfInputFile.filterData(record, filterCache)
      val qual = record.qual.toString
      if (qual != "NaN") {
        info += "QUAL" -> Seq(qual)
      }
      val genotypeLikelihood: Option[Seq[Double]] = (info remove "GL").map(_.map( _.toDouble))

      Call(
        callSetId = callSet,
        genotype = genotype,
        genotypeLikelihood = genotypeLikelihood,
        // Sort info alphabetically, for easier comparison during tests
        info = immutable.ListMap(info.toSeq.sortBy(_._1):_*)
      )
    }
  }

  private class BcfIterator extends Iterator[Option[Position]] {
    private var moreData: Boolean = true

    override def hasNext: Boolean = moreData

    override def next: Option[Position] = {
      if (bcf_read(dataFile, headerPtr, recordPtr) >= 0) {
        Some(Position(record.pos, record.pos + record.rlen, contigMap(record.rid)))
      } else {
        moreData = false
        None
      }
    }
  }

  lazy val iterator: Iterator[Position] = new BcfIterator().collect[Position] { case Some(x) => x }
}

case class BcfInputFileOpener(numAttempts: Int, backoffInSeconds: Int) extends LazyLogging {

  def open(filePath: String): Pointer[htslib.htsFile] = {

    // TODO: set hts_verbose to a value < 2
    // otherwise htslib spits out filename to stderr upon error, transient or otherwise
    // which would leak sampleId to logs
    //
    // code sample below is relevant but doesn't work since we manually manipulate htslib name in
    // build-htslib-bridj.sh
    //
    //      BridJ.getNativeLibrary("hts").getSymbolPointer("hts_verbose").setInt(0)
    //      (new HtslibLibrary).hts_verbose(0)

    val dataFilePath = Pointer.pointerToCString(filePath)
    val dataFileMode = Pointer.pointerToCString("r")

    def openAttempt(i: Int): Pointer[htslib.htsFile] = {
      Thread sleep (i * backoffInSeconds * 1000)
      val result = hts_open(dataFilePath, dataFileMode)
      if (result == Pointer.NULL) {
        logger.warn(s"failed to open file on attempt $i")
      }
      result
    }

    val opt: Option[Pointer[htslib.htsFile]] = {
      Stream.from(0) take numAttempts map openAttempt collectFirst {
        case p if p != Pointer.NULL => p
      }
    }

    if (opt.isDefined) { opt.get }
    else { throw new IOException(s"Failed to open file after $numAttempts attempts") }
  }
}

object BcfInputFile {
  //scalastyle:off magic.number
  val bcf_int8_missing: Byte = -128
  val bcf_int16_missing: Short = -32768
  val bcf_int32_missing: Int = -2147483647 - 1
  val bcf_float_missing = 0x7F800001
  val bcf_str_missing: Char = 0x07
  val bcf_str_vector_end: Char = 0
  //scalastyle:on

  def contigMap(header: bcf_hdr_t): Map[Int, String] = {
    val contigField = header.id.get(BCF_DT_CTG)
    val numContigs = header.n.get(BCF_DT_CTG)
    val validContigs = (0 until numContigs) map {
      contigField.get(_)
    } takeWhile {
      pair => Option(pair.key).isDefined
    } map {
      pair => pair.key.getCString
    }
    (Stream.from(0) zip validContigs).toMap
  }

  def genotype(record: bcf1_t, cache: scala.collection.mutable.Map[Int, String]): Seq[Int] = {
    val fmt = record.d.fmt.get(0)
    val fmtStr = cache(fmt.id)
    require(fmtStr == "GT")
    fmt.p.getBytes(fmt.n).map(bcfGtAllele)
  }

  def additionalGenotypeData(record: bcf1_t,
                             cache: mutable.Map[Int, String]): Map[String, Seq[String]] = {
    (for (i <- 0 until record.n_fmt) yield {
      val fmt = record.d.fmt.get(i)
      val fmtStr = cache(fmt.id)
      val data = BcfInputFile.parseMultipleValues(fmt.p, fmt.n, fmt.`type`)
      (fmtStr, data)
    }).toMap.filterKeys { _ != "GT" }
  }

  def filterData(record: bcf1_t, cache: scala.collection.mutable.Map[Int, String]): Seq[String] = {
    for (i <- 0 until record.d.n_flt) yield {
      cache(record.d.flt.get(i))
    }
  }

  def fieldName(header: bcf_hdr_t, fieldType: Int, id: Int): String = {
    header.id.get(fieldType).get(id).key.getCString
  }

  private def bcfGtAllele(i: Byte): Int = (i >> 1) - 1

  def parseInfoSingleValue(info: bcf_info_t): Seq[String] = Seq(info.`type` match {
    case BCF_BT_INT8 => toGa4ghString(info.v1.i.toByte)
    case BCF_BT_INT16 => toGa4ghString(info.v1.i.toShort)
    case BCF_BT_INT32 => toGa4ghString(info.v1.i)
    case BCF_BT_FLOAT => if (info.v1.i == bcf_float_missing) "." else info.v1.f.toString
    case BCF_BT_CHAR => toGa4ghString(info.v1.i.toChar.toString)
  })

  def parseInfoMultipleValues(info: bcf_info_t): Seq[String] = {
    parseMultipleValues(info.vptr, info.len, info.`type`)
  }

  def parseMultipleValues(ptr: Pointer[java.lang.Byte], len: Int, typ: Int): Seq[String] = {
    //scalastyle:off magic.number
    def pointerTraversal: Seq[String] = (0 until len) map { i =>
      typ match {
        case BCF_BT_INT8 => toGa4ghString(ptr.getByteAtIndex(i))
        // This nonsense with the ByteBuffer is apparently necessary to avoid alignment errors
        case BCF_BT_INT16 => toGa4ghString(ptr.getByteBufferAtOffset(i * 2, 2).getShort)
        case BCF_BT_INT32 => toGa4ghString(ptr.getByteBufferAtOffset(i * 4, 4).getInt)
        case BCF_BT_FLOAT =>
          val bytes = ptr.getByteBufferAtOffset(i * 4, 4)
          if (bytes.getInt(0) == bcf_float_missing) "." else bytes.getFloat(0).toString
      }
    }
    //scalastyle:on

    def stringTraversal: Seq[String] = {
      val s = ptr.getBytes(len).map(_.toChar).takeWhile(_ != bcf_str_vector_end).mkString
      s.split(",").map(toGa4ghString(_))
    }

    typ match {
      case BCF_BT_CHAR => stringTraversal
      case _ => pointerTraversal
    }
  }

  def toGa4ghString(a: Any): String = a match {
    case b: Byte =>
      if (b == bcf_int8_missing) { "." }
      else { b.toString }
    case s: Short =>
      if (s == bcf_int16_missing) { "." }
      else { s.toString}
    case i: Int =>
      if (i == bcf_int32_missing) { "." }
      else { i.toString }
    case t: String =>
      if (t.length > 0 && t.charAt(0) == bcf_str_missing) { "." }
      else { URLDecoder.decode(t, "utf-8") }
  }
}
