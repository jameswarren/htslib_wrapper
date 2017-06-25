import java.io.{FileWriter, PrintWriter}

import resource._
import sbt._
import sbt.Keys._

import scala.io.Source

object Jnaerate {

  lazy val generate: TaskKey[Seq[File]] = taskKey[Seq[java.io.File]]("generates Scala code for native C library")

  case class JnaerateTask(pkgName: String, libName: String, inputs: Seq[File], outputDir: File) {
    def generate(s: TaskStreams): Seq[File] = {

      val jnaerate = FileFunction.cached(s.cacheDirectory / "jnaerate", inStyle = FilesInfo.lastModified) {
        action(s.log)
      }

      jnaerate(inputs.toSet).toSeq
    }

    def action(logger: Logger): Set[File] => Set[File] = {
      files =>
        files.size match {
          case 0 => Set.empty[File]
          case _ =>
            outputDir.mkdirs()
            val jnaeratorArgs: List[String] = List(
              "-package", pkgName,
              "-library", libName,
              "-runtime", "BridJ",
              "-mode", "Directory",
              "-scalaStructSetters",
              "-skipFunctions", "cram.*",
              "-o", outputDir.getCanonicalPath)  ++
              (inputs map {
                _.getCanonicalPath
              })

            logger.info(s"Running JNAerator with args: ${jnaeratorArgs.mkString(" ")}")
            com.ochafik.lang.jnaerator.JNAerator.main(jnaeratorArgs.toArray)
            copyLibrary()
            (outputDir ** "*.java").get.toSet
        }
    }

    def copyLibrary(): Unit = {
      val nameFields = libName split "\\."
      val target = nameFields map { _.capitalize } mkString ""
      val replacement = nameFields(0).capitalize

      for {
        in <- managed(Source.fromFile(outputDir / pkgName / s"${target}Library.java"))
        out <- managed(new PrintWriter(new FileWriter(outputDir / pkgName / s"${replacement}Library.java")))
      } {
        in.getLines foreach {
          s => out.println(s.replaceAll(target, replacement))
        }
      }
    }
  }

}