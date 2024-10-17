import Dependencies.Kalix

lazy val `akka-javasdk-root` = project
  .in(file("."))
  .aggregate(akkaJavaSdkAnnotationProcessor, akkaJavaSdk, akkaJavaSdkTestKit)
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

lazy val akkaJavaSdk =
  Project(id = "akka-javasdk", base = file("akka-javasdk"))
    .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "akka-javasdk",
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
      buildInfoPackage := "akka.javasdk",
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
        "Akka SDK for Java",
        "-noqualifier",
        "java.lang"),
      Compile / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Server, AkkaGrpc.Client),
      Compile / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Scala),
      Compile / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "main",
      Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client),
      Test / PB.protoSources ++= (Compile / PB.protoSources).value,
      Test / PB.targets += PB.gens.java -> crossTarget.value / "akka-grpc" / "test",
      Test / envVars ++= Map("ENV" -> "value1", "ENV2" -> "value2"))
    .settings(Dependencies.javaSdk)

lazy val akkaJavaSdkTestKit =
  Project(id = "akka-javasdk-testkit", base = file("akka-javasdk-testkit"))
    .dependsOn(akkaJavaSdk)
    .enablePlugins(AkkaGrpcPlugin, BuildInfoPlugin, Publish, IntegrationTests)
    .disablePlugins(CiReleasePlugin) // we use publishSigned, but use a pgp utility from CiReleasePlugin
    .settings(commonCompilerSettings)
    .settings(disciplinedScalacSettings)
    .settings(
      name := "akka-javasdk-testkit",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"),
      Compile / scalacOptions ++= Seq("-release", "17"),
      buildInfoKeys := Seq[BuildInfoKey](
        name,
        version,
        "runtimeImage" -> Kalix.RuntimeImage,
        "runtimeVersion" -> Kalix.RuntimeVersion,
        "scalaVersion" -> scalaVersion.value),
      buildInfoPackage := "akka.javasdk.testkit",
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
        "Akka SDK for Java Testkit",
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
  "https://github.com/lightbend/akka-javasdk/tree/" + branch
}

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
    .settings(commonCompilerSettings)
    .settings(
      name := "akka-javasdk-annotation-processor",
      crossPaths := false,
      Compile / javacOptions ++= Seq("--release", "21"))
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
