import Jnaerate._
import scala.io.Source

name := "varstore"

organization in ThisBuild := "com.helix"

scalaVersion in ThisBuild := "2.11.11"

version in ThisBuild := Source.fromFile("VERSION").getLines.next

lazy val htsLibName: SettingKey[String] = settingKey[String]("filename of htslib dynamic library")

def headerFiles(baseDir: File): Seq[File] = {
  val finder: PathFinder = baseDir * "*.h"
  finder.get
}

def project(name: String, directory: String): Project = {
  Project(name, file(directory)).settings(
    libraryDependencies ++= Seq(
      "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
      "org.scalatest" %% "scalatest" % "3.0.3" % "test",
      "org.pegdown" % "pegdown" % "1.6.0" % "test",
      "org.mockito" % "mockito-core" % "2.2.9" % "test",
      "com.lihaoyi" % "ammonite_2.11.8" % "0.8.0" % "test"
    ),
    initialCommands in (Test, console) := """ammonite.Main().run()"""
  )
}

lazy val htslib = project("htslib", "work/htslib")

lazy val varstore = project("varstore", "varstore").settings(
  htsLibName := {
    sys.props.get("os.name") match {
      case Some(s) if s.startsWith("Mac") => "libhts.dylib"
      case _ => "libhts.so"
    }
  },
  Jnaerate.generate := {
    val headerDir = baseDirectory.in(htslib).value / "include" / "htslib"
    val dynLib = baseDirectory.in(htslib).value / "lib" / htsLibName.value
    val headers = headerFiles(headerDir)
    val options = Seq("-mode", "Directory", "-scalaStructSetters", "-skipFunctions", "cram.*")
    JnaerateTask(
      pkgName = "htslib",
      libName = htsLibName.value,
      inputs = headers :+ dynLib,
      outputDir = sourceManaged.value / "main").generate(Jnaerate.Runtime.BRIDJ, options, streams.value)
  },
  sourceGenerators in Compile += Jnaerate.generate,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  compile in Compile := {
    val libDir = (managedSourceDirectories in Compile).value.head / "lib"
    val classDir = (classDirectory in Compile).value
    org.apache.commons.io.FileUtils.copyDirectoryToDirectory(libDir, classDir)
    (compile in Compile).value
  },

  libraryDependencies ++= Seq(
    "com.jsuereth" %% "scala-arm" % "2.0",
    "com.nativelibs4java" % "bridj" % "0.7.0",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.json4s" %% "json4s-native" % "3.5.2",
    "commons-io" % "commons-io" % "2.5" % "test"
  )
)
