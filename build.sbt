import scala.io.Source
import scala.util.Try

name := "variantstore"

organization in ThisBuild := "com.helix"

scalaVersion in ThisBuild := "2.11.8"

version in ThisBuild := Try { Source.fromFile("VERSION").getLines.next }.getOrElse("unknown")

retrieveManaged in ThisBuild := true

lazy val htsLibName: SettingKey[String] = settingKey[String]("filename of htslib dynamic library")

def headerFiles(baseDir: File): Seq[File] = {
  val finder: PathFinder = baseDir * "*.h"
  finder.get
}

def helixProject(name: String, directory: String): Project = {

  val mergeSuffixes = List(".RSA", ".xsd", ".dtd", ".properties")

  Project(name, file(directory)).
    settings(
      libraryDependencies ++= Seq(
        "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
        "org.scalatest" %% "scalatest" % "3.0.3" % "test",
        "org.pegdown" % "pegdown" % "1.6.0" % "test",
        "org.mockito" % "mockito-core" % "2.2.9" % "test",
        "com.lihaoyi" % "ammonite" % "0.8.0" % "test" cross CrossVersion.full
      ),
      assemblyMergeStrategy in assembly := {
        case PathList("META-INF", xs @ _*) => MergeStrategy.discard
        case PathList(xs @ _*) if mergeSuffixes.exists(xs.last.endsWith) => MergeStrategy.first
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      },
      test in assembly := {},
      initialCommands in (Test, console) := """ammonite.Main().run()""",
      testOptions in Test += Tests.Argument("-o", "-h",
        (target.value / "test-reports-html").absolutePath, "-fW",
        (target.value / "test-log.txt").absolutePath)
    )
}

lazy val htslib = helixProject("htslib", "work/htslib")

lazy val hvac = helixProject("varstore", "varstore").settings(
  htsLibName := {
    sys.props.get("os.name") match {
      case Some(s) if s.startsWith("Mac") => "libhts.dylib"
      case _ => "libhts.so"
    }
  },
  Jnaerate.generate := {
    val headerDir = baseDirectory.in(htslib).value / "htslib"
    val dynLib = baseDirectory.in(htslib).value / htsLibName.value
    val headers = headerFiles(headerDir)
    Jnaerate.JnaerateTask(
      pkgName = "htslib",
      libName = htsLibName.value,
      inputs = headers :+ dynLib,
      outputDir = sourceManaged.value / "main").generate(streams.value)
  },
  sourceGenerators in Compile += Jnaerate.generate,
  javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint"),
  scalacOptions ++= Seq("-feature"),
  compile in Compile := {
    val libDir = (managedSourceDirectories in Compile).value.head / "lib"
    val classDir = (classDirectory in Compile).value
    org.apache.commons.io.FileUtils.copyDirectoryToDirectory(libDir, classDir)
    (compile in Compile).value
  },
  libraryDependencies ++= Seq(
    "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
    "com.amazonaws" % "aws-java-sdk-s3" % "1.11.49",
    "com.github.scopt" %% "scopt" % "3.6.0",
    "com.jsuereth" %% "scala-arm" % "2.0",
    "com.nativelibs4java" % "bridj" % "0.7.0",
    "com.rollbar" % "rollbar" % "0.5.3",
    "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
    "org.json4s" %% "json4s-native" % "3.5.2",
    "org.slf4j" % "slf4j-api" % "1.7.12",
    "org.slf4j" % "slf4j-log4j12" % "1.7.12",
    "org.scalaj" %% "scalaj-http" % "2.3.0",
    "commons-io" % "commons-io" % "2.5" % "test"
  )
)
