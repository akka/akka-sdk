import sbt._
import sbt.Keys._
import akka.grpc.sbt.AkkaGrpcPlugin
import com.lightbend.sbt.JavaFormatterPlugin.autoImport.javafmtOnCompile
import de.heikoseeberger.sbtheader.{ AutomateHeaderPlugin, HeaderPlugin }
import org.scalafmt.sbt.ScalafmtPlugin
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbtprotoc.ProtocPlugin
import scala.collection.breakOut

object CommonSettings extends AutoPlugin {

  override def requires = plugins.JvmPlugin && ScalafmtPlugin
  override def trigger = allRequirements

  override def globalSettings =
    Seq(
      organization := "io.akka",
      organizationName := "Lightbend Inc.",
      organizationHomepage := Some(url("https://akka.io")),
      homepage := Some(url("https://akka.io")),
      description := "Akka SDK for Java",
      resolvers += "Akka repository".at("https://repo.akka.io/maven"),
      developers := List(
        Developer(
          id = "akka-developers",
          name = "Akka SDK Developers",
          email = "akka.official@gmail.com",
          url = url("https://akka.io"))),
      scmInfo := Some(
        ScmInfo(url("https://github.com/akka/akka-sdk"), "scm:git@github.com:akka/akka-sdk.git")),
      releaseNotesURL := (
        if ((ThisBuild / isSnapshot).value) None
        else Some(url(s"https://github.com/akka/akka-sdk/releases/tag/v${version.value}"))
      ),
      startYear := Some(2021),
      licenses := {
        val tagOrBranch =
          if (version.value.endsWith("SNAPSHOT")) "main"
          else "v" + version.value
        Seq(("BUSL-1.1", url(s"https://raw.githubusercontent.com/akka/akka-sdk/${tagOrBranch}/LICENSE")))
      },
      scalafmtOnCompile := !insideCI.value,
      javafmtOnCompile := !insideCI.value,
      scalaVersion := Dependencies.ScalaVersion,
      Compile / javacOptions ++= Seq("-encoding", "UTF-8", "--release", "21"),
      Compile / scalacOptions ++= Seq(
        "-encoding",
        "UTF-8",
        "-deprecation",
        // scalac doesn't do 21
        "-release",
        "17"),
      run / javaOptions ++= {
        sys.props.collect { case (key, value) if key.startsWith("akka") => s"-D$key=$value" }(breakOut)
      }) ++ (
      if (sys.props.contains("disable.apidocs"))
        Seq(Compile / doc / sources := Seq.empty, Compile / packageDoc / publishArtifact := false)
      else Seq.empty
    ) ++ disciplinedScalacSettings

  lazy val sharedScalacOptions =
    Seq("-feature", "-unchecked")

  lazy val fatalWarnings = Seq(
    "-Xfatal-warnings", // discipline only in Scala 2 for now
    "-Wconf:src=.*/target/.*:s",
    // silence warnings from generated sources
    "-Wconf:src=.*/src_managed/.*:s",
    // silence warnings from deprecated protobuf fields
    "-Wconf:src=.*/akka-grpc/.*:s")

  lazy val scala212Options = sharedScalacOptions ++ fatalWarnings

  lazy val scala213Options = scala212Options ++
    Seq("-Wunused:imports,privates,locals")

  // -Wconf configs will be available once https://github.com/scala/scala3/pull/20282 is merged and 3.3.4 is released
  lazy val scala3Options = sharedScalacOptions ++ Seq("-Wunused:imports,privates,locals")

  def disciplinedScalacSettings: Seq[Setting[_]] = {
    if (sys.props.get("akka.javasdk.no-discipline").isEmpty) {
      Seq(
        Compile / scalacOptions ++= {
          if (scalaVersion.value.startsWith("3.")) scala3Options
          else if (scalaVersion.value.startsWith("2.13")) scala213Options
          else scala212Options
        },
        Compile / doc / scalacOptions := (Compile / doc / scalacOptions).value.filterNot(_ == "-Xfatal-warnings"))
    } else Seq.empty
  }

  override def projectSettings = Seq(run / fork := true, Test / fork := true, Test / javaOptions ++= Seq("-Xms1G"))
}

object CommonHeaderSettings extends AutoPlugin {

  override def requires = HeaderPlugin
  override def trigger = allRequirements

  import HeaderPlugin.autoImport._
  import de.heikoseeberger.sbtheader.FileType

  override def projectSettings = AutomateHeaderPlugin.projectSettings ++ Seq(
    headerLicense := Some(
      HeaderLicense.Custom("""Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>""")),
    headerMappings += FileType("proto") -> HeaderCommentStyle.cppStyleLineComment,
    headerSources / excludeFilter := (headerSources / excludeFilter).value || "package-info.java",
    // exclude source files in resources
    headerResources / excludeFilter := (headerSources / excludeFilter).value || "*.java" || "*.scala")
}

object CommonAkkaGrpcSettings extends AutoPlugin {

  override def requires = AkkaGrpcPlugin
  override def trigger = allRequirements

  import AkkaGrpcPlugin.autoImport._
  import ProtocPlugin.autoImport._

  override def projectSettings = Seq(
    akkaGrpcCodeGeneratorSettings := Seq(), // remove `flat_package` setting added by Akka gRPC
    // protobuf external sources are filtered out by sbt-protoc and then added again by sbt-akka-grpc
    Compile / unmanagedResourceDirectories := (Compile / unmanagedResourceDirectories).value
      .filterNot(_ == PB.externalSourcePath.value),
    Test / unmanagedResourceDirectories := (Test / unmanagedResourceDirectories).value
      .filterNot(_ == PB.externalSourcePath.value))
}
