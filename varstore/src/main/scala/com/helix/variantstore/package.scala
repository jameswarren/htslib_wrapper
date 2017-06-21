// © 2016 Helix OpCo LLC. All rights reserved.
// Initial Author: James Warren

package com.helix

import com.typesafe.scalalogging.LazyLogging
import org.json4s.JsonAST.{JInt, JString}
import org.json4s.native.Serialization
import org.json4s.{NoTypeHints, _}

import scala.collection.mutable

package object variantstore {

  // Fields in alphabetical order, for easier comparison with hgapi v0
  final case class Call(
      callSetId: String,
      genotype: Seq[Int],
      genotypeLikelihood: Option[Seq[Double]],
      info: Map[String, Seq[String]])

  final case class Variant(
      alternateBases: Seq[String],
      calls: Seq[Call],
      end: Long,
      referenceBases: String,
      referenceName: String,
      start: Long) {

    lazy val isReferenceOrNoCallBlock: Boolean = {
      alternateBases == Seq("<NON_REF>")
    }
  }

  final case class Region(chrom: String, chromStart: Int, chromEnd: Int, ra: Option[String]) {
    // intentionally throws Exception if reference is undefined
    lazy val initialReferenceBase: String = ra.get.head.toString
  }

  final case class HvacException(message: String, code: String,
      partnerAppCustomerId: Option[String]) extends Exception(message)

  final case class ErrorResponse(error: HvacException)

  def cache[K, V](lookup: K => V): mutable.Map[K,V] = {
    new mutable.HashMap[K,V] {
      override def apply(k: K): V = getOrElseUpdate(k, lookup(k))
    }
  }

  def getProperty(key: String): Option[String] = {
    Seq(sys.env.get(key), sys.props.get(key)).collectFirst { case Some(v) => v }
  }

  implicit class CodeWrappers[T](block: => T) extends LazyLogging {
    def wallClockTime(msg: String): T = {
      val t0 = System.nanoTime()
      val result = block // call-by-name
      val t1 = System.nanoTime()
      val delta = t1 - t0
      logger.info(s"$msg:: elapsed time: $delta ns, ${delta / 1e9} s")
      result
    }
  }

  object JsonParsing {
    // 1) if a field should be an integer but is represented as a string in JSON, automatically
    // convert
    // 2) use strings to represent long values in JSON

    class StringToNumSerializer extends CustomSerializer[Int](_  => (
      { // deserializer
        case JInt(x) => x.toInt
        case JString(x) => x.toInt
      }: PartialFunction[JValue, Int],
      { // serializer
        case x: Int => JInt(x)
        case x: Long => JString(x.toString)
      }: PartialFunction[Any, JValue]))

    implicit val formats: Formats = Serialization.formats(NoTypeHints) + new StringToNumSerializer()
  }
}