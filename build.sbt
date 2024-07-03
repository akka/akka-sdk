import scala.collection.immutable.Seq

import Dependencies.Kalix
import com.jsuereth.sbtpgp.PgpKeys.publishSignedConfiguration

lazy val `kalix-jvm-sdk` = project
  .in(file("."))
  .aggregate(devTools, devToolsInternal, annotationProcessor, coreSdk, javaSdkSpring, javaSdkSpringTestKit)
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
  if (sys.props.get("kalix.no-discipline").isEmpty) {
    Seq(
      Compile / scalacOptions ++= {
        if (scalaVersion.value.startsWith("3.")) scala3Options
        else if (scalaVersion.value.startsWith("2.13")) scala213Options
        else scala212Options
      },
      Compile / doc / scalacOptions := (Compile / doc / scalacOptions).value.filterNot(_ == "-Xfatal-warnings"))
  } else Seq.empty
}

lazy val coreSdk = project
  .in(file("sdk/core"))
  .enablePlugins(Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .dependsOn(devTools)
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-jvm-core-sdk",
    // only packages that are to be released with scala 3 should have the _3 appended
    // i.e. java sdk package names should remain without suffix
    crossPaths := scalaVersion.value.startsWith("3."),
    Compile / javacOptions ++= Seq("--release", "11"),
    Compile / scalacOptions ++= Seq("-release", "11"),
    crossScalaVersions := Dependencies.CrossScalaVersions,
    // Generate javadocs by just including non generated Java sources
    Compile / doc / sources := {
      val javaSourceDir = (Compile / javaSource).value.getAbsolutePath
      (Compile / doc / sources).value.filter(_.getAbsolutePath.startsWith(javaSourceDir))
    })

lazy val javaSdkSpring = project
  .in(file("sdk/java-sdk-spring"))
  .dependsOn(coreSdk)
  .dependsOn(devTools)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-java-sdk-spring",
    crossPaths := false,
    Compile / javacOptions ++= Seq("--release", "21"),
    Compile / scalacOptions ++= Seq("-release", "21"),
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
    buildInfoPackage := "kalix.spring",
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
      "Kalix Java SDK for Spring",
      "-noqualifier",
      "java.lang"),
    Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client),
    Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
    Compile / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "main",
    Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
    Test / PB.protoSources ++= (Compile / PB.protoSources).value,
    Test / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "test")
  .settings(Dependencies.javaSdk)
  .settings(Dependencies.javaSdkSpring)

lazy val javaSdkSpringTestKit = project
  .in(file("sdk/java-sdk-spring-testkit"))
  .dependsOn(javaSdkSpring)
  .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish, IntegrationTests)
  .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
  .settings(commonCompilerSettings)
  .settings(disciplinedScalacSettings)
  .settings(
    name := "kalix-java-sdk-spring-testkit",
    crossPaths := false,
    Compile / javacOptions ++= Seq("--release", "21"),
    Compile / scalacOptions ++= Seq("-release", "21"),
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      "runtimeImage" -> Kalix.RuntimeImage,
      "runtimeVersion" -> Kalix.RuntimeVersion,
      "scalaVersion" -> scalaVersion.value),
    buildInfoPackage := "kalix.spring.testkit",
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
      "Kalix Java SDK Testkit for Spring",
      "-noqualifier",
      "java.lang"))
  .settings(inConfig(IntegrationTest)(JupiterPlugin.scopedSettings): _*)
  .settings(Dependencies.javaSdkSpringTestKit)

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
  "https://github.com/lightbend/kalix-jvm-sdk/tree/" + branch
}

lazy val devTools = devToolsCommon(
  project
    .in(file("devtools"))
    .settings(
      name := "kalix-devtools",
      scalaVersion := Dependencies.ScalaVersion,
      crossScalaVersions := Dependencies.CrossScalaVersions))

/*
  This variant devTools compiles with Scala 2.12, but uses the same source files as the 2.13 version (above).
  This is needed because the devTools artifact is also used by the sbt and mvn plugins and therefore needs a 2.12 version.
  Note that crossbuilding is not an option, because when the built selects 2.13, it will try to build sbt-kalix with 2.13
 */
lazy val devToolsInternal =
  devToolsCommon(
    project
      .in(file("devtools"))
      .settings(
        name := "kalix-devtools-internal",
        scalaVersion := Dependencies.ScalaVersionForTooling,
        // to avoid overwriting the 2.13 version
        target := baseDirectory.value / "target-2.12"))

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
      buildInfoPackage := "kalix.devtools",
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
        "Kalix Dev Tools",
        "-noqualifier",
        "java.lang"))
    .settings(Dependencies.devTools)

lazy val samplesCompilationProject: CompositeProject =
  SamplesCompilationProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(javaSdkSpring)
      .dependsOn(javaSdkSpringTestKit % "compile->test")
  }

lazy val annotationProcessor =
  Project(id = "annotation-processor", base = file("annotation-processor"))
    .enablePlugins(Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(
      name := "kalix-java-sdk-annotation-processor",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"))
    .settings(libraryDependencies += Dependencies.typesafeConfig)

lazy val annotationProcessorTestProject: CompositeProject =
  AnnotationProcessorTestProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(javaSdkSpring)
      .dependsOn(annotationProcessor)
      .settings(libraryDependencies += Dependencies.scalaTest % Test)
      .settings(
        libraryDependencies += Dependencies.scalaTest % Test,
        Compile / javacOptions ++= Seq("-processor", "kalix.javasdk.tooling.processor.ComponentAnnotationProcessor"))
  }

addCommandAlias("formatAll", "scalafmtAll; javafmtAll")
