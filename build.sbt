import Dependencies.Kalix
import Dependencies.AkkaRuntimeVersion
import scala.xml.Elem
import scala.xml.Node
import scala.xml.TopScope

import Dependencies.AkkaGrpcVersion

lazy val `akka-javasdk-root` = project
  .in(file("."))
  .aggregate(akkaJavaSdkAnnotationProcessor, akkaJavaSdk, akkaJavaSdkTestKit, akkaJavaSdkTests, akkaJavaSdkParent)
  // samplesCompilationProject and annotationProcessorTestProject are composite project
  // to aggregate them we need to map over them
  .aggregate(samplesCompilationProject.componentProjects.map(p => p: ProjectReference): _*)
  .aggregate(annotationProcessorTestProject.componentProjects.map(p => p: ProjectReference): _*)
  .settings(
    publish / skip := true,
    publishTo := None,
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
        "runtimeVersion" -> AkkaRuntimeVersion,
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
        "runtimeVersion" -> AkkaRuntimeVersion,
        "scalaVersion" -> scalaVersion.value),
      buildInfoPackage := "akka.javasdk.testkit",
      // eventing testkit client
      akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client))
    .settings(DocSettings.forModule("Akka SDK Testkit"))
    .settings(Dependencies.javaSdkTestKit)

lazy val akkaJavaSdkTests =
  Project(id = "akka-javasdk-tests", base = file("akka-javasdk-tests"))
    .enablePlugins(AkkaGrpcPlugin)
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
      publishTo := None,
      doc / sources := Seq.empty,
      // generating test service
      Test / akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
      Test / akkaGrpcGeneratedSources := Seq(AkkaGrpc.Client, AkkaGrpc.Server),
      Test / akkaGrpcCodeGeneratorSettings ++= CommonSettings.serviceGrpcGeneratorSettings,
      Test / PB.protoSources ++= (Compile / PB.protoSources).value)
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

lazy val akkaJavaSdkParent =
  Project(id = "akka-javasdk-parent", base = file("akka-javasdk-parent"))
    .enablePlugins(BuildInfoPlugin, CiReleasePlugin)
    .disablePlugins(Publish)
    .settings(
      name := "akka-javasdk-parent",
      crossPaths := false,
      scalaVersion := Dependencies.ScalaVersion,
      publish / skip := false,
      publishArtifact := false, // disable jar, sources, docs
      makePom / publishArtifact := true, // only pom
      pomPostProcess := { (node: Node) =>
        // completely replace with our pom.xml
        val pom = scala.xml.XML.loadFile(baseDirectory.value / "pom.xml")
        // but use the current version
        updatePomVersion(pom, version.value, AkkaRuntimeVersion, AkkaGrpcVersion)
      })

def updatePomVersion(node: Elem, v: String, runtimeVersion: String, akkaGrpcVersion: String): Elem = {
  def updateElements(seq: Seq[Node]): Seq[Node] = {
    seq.map {
      case version @ <version>{_}</version> =>
        <version>{v}</version>
      case ps @ <properties>{ch @ _*}</properties> =>
        val updatedProperties =
          ch.map {
            case <akka-runtime.version>{_}</akka-runtime.version> =>
              <akka-runtime.version>{runtimeVersion}</akka-runtime.version>
            case <akka-javasdk.version>{_}</akka-javasdk.version> =>
              <akka-javasdk.version>{v}</akka-javasdk.version>
            case <akka.grpc.version>{_}</akka.grpc.version> =>
              <akka.grpc.version>{akkaGrpcVersion}</akka.grpc.version>
            case other =>
              other
          }
        ps.asInstanceOf[Elem].copy(child = updatedProperties)
      case other => other
    }
  }

  node match {
    case p @ <project>{ch @ _*}</project> =>
      // important to copy to keep the namespace attributes
      p.copy(child = updateElements(ch))
    case other =>
      other
  }
}

addCommandAlias("formatAll", "scalafmtAll; javafmtAll")
