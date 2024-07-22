import scala.collection.immutable.Seq

import Dependencies.Kalix
import com.jsuereth.sbtpgp.PgpKeys.publishSignedConfiguration

lazy val `akka-platform-jvm-sdk` = project
  .in(file("."))
  .aggregate(devTools, annotationProcessor, javaSdk, javaSdkTestKit)
  .settings(
    (publish / skip) := true,
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossScalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.ScalaVersion)

def commonCompilerSettings: Seq[Setting[_]] =
  Seq(
    Compile / javacOptions ++= Seq("-encoding", "UTF-8"),
    Compile / scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation"))

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
  if (sys.props.get("akka.platform.no-discipline").isEmpty) {
    Seq(
      Compile / scalacOptions ++= {
        if (scalaVersion.value.startsWith("3.")) scala3Options
        else if (scalaVersion.value.startsWith("2.13")) scala213Options
        else scala212Options
      },
      Compile / doc / scalacOptions := (Compile / doc / scalacOptions).value.filterNot(_ == "-Xfatal-warnings"))
  } else Seq.empty
}

lazy val javaSdk =
  Project(id = "java-sdk", base = file("sdk/java-sdk"))
    .dependsOn(devTools)
    .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "akka-platform-java-sdk",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"),
      Compile / scalacOptions ++= Seq("-release", "17"),
      scalaVersion := Dependencies.ScalaVersion,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion,
        "protocolMajorVersion" -> Kalix.ProtocolVersionMajor,
        "protocolMinorVersion" -> Kalix.ProtocolVersionMinor,
        "scalaVersion" -> scalaVersion.value,
        "akkaVersion" -> Dependencies.AkkaVersion),
      buildInfoPackage := "akka.platform.javasdk",
      Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server),
      Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
      Test / javacOptions ++= Seq("-parameters"), // for Jackson
      // Generate javadocs by just including non generated Java sources
      Compile / doc / sources := {
        val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
        (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
      },
      // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
      // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
      Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
      Compile / doc / javacOptions ++= Seq(
        "-Xdoclint:none",
        "-overview",
        ((Compile / javaSource).value / "overview.html").getAbsolutePath,
        "-notimestamp",
        "-doctitle",
        "Akka Platform Java SDK",
        "-noqualifier",
        "java.lang"),
      Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client),
      Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
      Compile / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "main",
      Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
      Test / PB.protoSources ++= (Compile / PB.protoSources).value,
      Test / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "test")
    .settings(Dependencies.javaSdk)

lazy val javaSdkTestKit =
  Project(id = "java-sdk-testkit", base = file("sdk/java-sdk-testkit"))
    .dependsOn(javaSdk)
    .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish, IntegrationTests)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "akka-platform-java-sdk-testkit",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"),
      Compile / scalacOptions ++= Seq("-release", "17"),
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion,
        "scalaVersion" -> scalaVersion.value),
      buildInfoPackage := "akka.platform.javasdk.testkit",
      Test / javacOptions ++= Seq("-parameters"), // for Jackson
      IntegrationTest / javacOptions += "-parameters", // for Jackson
      // Generate javadocs by just including non generated Java sources
      Compile / doc / sources := {
        val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
        (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
      },
      // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
      // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
      Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
      Compile / doc / javacOptions ++= Seq(
        "-Xdoclint:none",
        "-overview",
        ((Compile / javaSource).value / "overview.html").getAbsolutePath,
        "-notimestamp",
        "-doctitle",
        "Akka Platform Java SDK Testkit",
        "-noqualifier",
        "java.lang"))
    .settings(inConfig(IntegrationTest)(JupiterPlugin.scopedSettings): _*)
    .settings(Dependencies.javaSdkTestKit)

def scaladocOptions(title: String, ver: String, base: File): List[String] = {
  val urlString = githubUrl(ver) + "/€{FILE_PATH_EXT}#L€{FILE_LINE}"
  List(
    "-implicits",
    "-groups",
    "-doc-source-url",
    urlString,
    "-sourcepath",
    base.getAbsolutePath,
    "-doc-title",
    title,
    "-doc-version",
    ver)
}

def githubUrl(v: String): String = {
  val branch = if (v.endsWith("SNAPSHOT")) "main" else "v" + v
  "https://github.com/lightbend/akka-platform-jvm-sdk/tree/" + branch
}

lazy val devTools = devToolsCommon(
  Project(id = "dev-tools", base = file("devtools"))
    .settings(
      name := "akka-platform-devtools",
      scalaVersion := Dependencies.ScalaVersion,
      crossScalaVersions := Dependencies.CrossScalaVersions))

/*
 Common configuration to be applied to devTools modules (both 2.12 and 2.13)
 We need to have this 'split' module because compilation of sbt plugins don't play nice with cross-compiled modules.
 Instead, it's easier to have a separate module for each Scala version, but share the same source files.
 */
def devToolsCommon(project: Project): Project =
  project
    .enablePlugins(BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    // TODO: need fix in KalixPlugin
    // .settings(disciplinedScalacSettings)
    .settings(
      Compile / javacOptions ++= Seq("--release", "11"),
      Compile / scalacOptions ++= Seq("-release", "11"),
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion),
      buildInfoPackage := "akka.platform.devtools",
      // Generate javadocs by just including non generated Java sources
      Compile / doc / sources := {
        val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
        (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
      },
      // javadoc (I think java 9 onwards) refuses to compile javadocs if it can't compile the entire source path.
      // but since we have java files depending on Scala files, we need to include ourselves on the classpath.
      Compile / doc / dependencyClasspath := (Compile / fullClasspath).value,
      Compile / doc / javacOptions ++= Seq(
        "-Xdoclint:none",
        "-overview",
        ((Compile / javaSource).value / "overview.html").getAbsolutePath,
        "-notimestamp",
        "-doctitle",
        "Akka Platform Dev Tools",
        "-noqualifier",
        "java.lang"))
    .settings(Dependencies.devTools)

lazy val samplesCompilationProject: CompositeProject =
  SamplesCompilationProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(javaSdk)
      .dependsOn(javaSdkTestKit % "compile->test")
  }

lazy val annotationProcessor =
  Project(id = "annotation-processor", base = file("annotation-processor"))
    .enablePlugins(Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(
      name := "akka-platform-java-sdk-annotation-processor",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"))
    .settings(libraryDependencies += Dependencies.typesafeConfig)

lazy val annotationProcessorTestProject: CompositeProject =
  AnnotationProcessorTestProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(javaSdk)
      .dependsOn(annotationProcessor)
      .settings(libraryDependencies += Dependencies.scalaTest % Test)
      .settings(
        libraryDependencies += Dependencies.scalaTest % Test,
        Compile / javacOptions ++= Seq(
          "-processor",
          "akka.platform.javasdk.tooling.processor.ComponentAnnotationProcessor"))
  }

addCommandAlias("formatAll", "scalafmtAll; javafmtAll")
