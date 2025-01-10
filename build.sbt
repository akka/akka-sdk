import Dependencies.Kalix

lazy val `akka-javasdk-root` = project
  .in(file("."))
  .aggregate(akkaJavaSdkAnnotationProcessor, akkaJavaSdk, akkaJavaSdkTestKit, akkaJavaSdkTests)
  .settings(
    (publish / skip) := true,
    // https://github.com/sbt/sbt/issues/3465
    // Libs and plugins must share a version. The root project must use that
    // version (and set the crossScalaVersions as empty list) so each sub-project
    // can then decide which scalaVersion and crossScalaVersions they use.
    crossScalaVersions := Nil,
    scalaVersion := Dependencies.ScalaVersion)

lazy val akkaJavaSdk =
  Project(id = "akka-javasdk", base = file("akka-javasdk"))
    .enablePlugins(BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(
      name := "akka-javasdk",
      crossPaths := false,
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
      buildInfoPackage := "akka.javasdk",
      Test / javacOptions ++= Seq("-parameters"), // for Jackson
      Test / envVars ++= Map("ENV" -> "value1", "ENV2" -> "value2"))
    .settings(DocSettings.forModule("Akka SDK"))
    .settings(Dependencies.javaSdk)

lazy val akkaJavaSdkTestKit =
  Project(id = "akka-javasdk-testkit", base = file("akka-javasdk-testkit"))
    .dependsOn(akkaJavaSdk)
    .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(
      name := "akka-javasdk-testkit",
      crossPaths := false,
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion,
        "scalaVersion" -> scalaVersion.value),
      buildInfoPackage := "akka.javasdk.testkit")
    .settings(DocSettings.forModule("Akka SDK Testkit"))
    .settings(Dependencies.javaSdkTestKit)

lazy val akkaJavaSdkTests =
  Project(id = "akka-javasdk-tests", base = file("akka-javasdk-tests"))
    .dependsOn(akkaJavaSdk, akkaJavaSdkTestKit)
    .settings(
      name := "akka-javasdk-testkit",
      crossPaths := false,
      Test / parallelExecution := false,
      Test / fork := true,
      // for Jackson
      Test / javacOptions ++= Seq("-parameters"),
      // only tests here
      publish / skip := true,
      doc / sources := Seq.empty)
    .settings(inConfig(Test)(JupiterPlugin.scopedSettings))
    .settings(Dependencies.tests)

lazy val samplesCompilationProject: CompositeProject =
  SamplesCompilationProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(akkaJavaSdk)
      .dependsOn(akkaJavaSdkTestKit % "compile->test")
  }

lazy val akkaJavaSdkAnnotationProcessor =
  Project(id = "akka-javasdk-annotation-processor", base = file("akka-javasdk-annotation-processor"))
    .enablePlugins(Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(name := "akka-javasdk-annotation-processor", crossPaths := false)
    .settings(libraryDependencies += Dependencies.typesafeConfig)

lazy val annotationProcessorTestProject: CompositeProject =
  AnnotationProcessorTestProject.compilationProject { sampleProject =>
    sampleProject
      .dependsOn(akkaJavaSdk)
      .dependsOn(akkaJavaSdkAnnotationProcessor)
      .settings(libraryDependencies += Dependencies.scalaTest % Test)
      .settings(
        libraryDependencies += Dependencies.scalaTest % Test,
        Compile / javacOptions ++= Seq("-processor", "akka.javasdk.tooling.processor.ComponentAnnotationProcessor"))
  }

addCommandAlias("formatAll", "scalafmtAll; javafmtAll")
