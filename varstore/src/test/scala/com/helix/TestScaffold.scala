// © 2016 Helix OpCo LLC. All rights reserved.
// Initial Author: James Warren

package com.helix

import java.io.File

import com.helix.variantstore.{Call, Variant}
import org.apache.commons.io.{FileUtils, FilenameUtils}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.scalatest.{FunSpec, GivenWhenThen, Matchers}
import org.scalatest.prop.Checkers
import resource._

//scalastyle:off magic.number
trait TestScaffold extends FunSpec with Matchers with Checkers with GivenWhenThen {

  object variantGenerator {
    val sampleCall: Call = Call(
      callSetId = "testing",
      genotype = Seq(0, 0),
      genotypeLikelihood = Some(Seq(-0, -2.7, -30.9)),
      info = Map(
        "DP" -> Seq("9"),
        "FILTER" -> Seq("LOWQ"),
        "GQ" -> Seq("27"),
        "MIN_DP" -> Seq("9")
      )
    )

    def genVariant(
        contig: String,
        start: Int,
        end: Int,
        alts: Seq[String],
        base: String): Variant = {
      Variant(
        alternateBases = alts,
        calls = Seq(sampleCall),
        end = end,
        referenceBases = base,
        referenceName = contig,
        start = start
      )
    }
  }

  def compareJson(actual: Seq[JValue], expected: Seq[JValue]): String = {
    val firstDifference: Option[(JValue, JValue)] = actual zip expected find {
      case (got, wanted) => got != wanted
    }
    val diffMessage: String = firstDifference.fold("") {
      case (got, wanted) =>
        val builder = new StringBuilder(pretty(render(got)))
        val Diff(changed, added, deleted) = wanted diff got
        if (changed != JNothing) {
          builder.append("\nCHANGED--\n" + pretty(render(changed)))
        }
        if (added != JNothing) {
          builder.append("\nADDED--\n" + pretty(render(added)))
        }
        if (deleted != JNothing) {
          builder.append("\nDELETED--\n" + pretty(render(deleted)))
        }
        builder.toString
    }
    diffMessage
  }

  def copyResourceFile(fileName: String, to: Option[String] = None): File = {
   val tempFile: File = {
     to match {
       case None => java.io.File.createTempFile("test", "." + FilenameUtils.getExtension(fileName))
       case Some(tmpFileName) => new File(tmpFileName)
     }
   }
   for {inputStream <- managed(getClass.getResourceAsStream(fileName))} {
     FileUtils.copyInputStreamToFile(inputStream, tempFile)
   }
   tempFile
  }

  def withResourceFile(fileName: String, testCode: File => Any): Unit = {
    val tempFile = copyResourceFile(fileName, None)
    try {
      testCode(tempFile)
    } finally tempFile.delete()
  }
}
//scalastyle:on magic.number