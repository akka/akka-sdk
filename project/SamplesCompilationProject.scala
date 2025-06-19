import akka.grpc.sbt.AkkaGrpcPlugin

import java.io.File
import de.heikoseeberger.sbtheader.HeaderPlugin
import sbt.*
import sbt.CompositeProject
import sbt.Keys.*
import sbt.Project
import sbt.ProjectReference
import sbt.Test

object SamplesCompilationProject {

  private val LangChain4JVersion = "1.0.0-beta1"
  private val additionalDeps = Map(
    "spring-dependency-injection" -> Seq("org.springframework" % "spring-context" % "6.2.8"),
    "ask-akka-agent" -> Seq(
      "dev.langchain4j" % "langchain4j-open-ai" % LangChain4JVersion,
      "dev.langchain4j" % "langchain4j" % LangChain4JVersion,
      "dev.langchain4j" % "langchain4j-mongodb-atlas" % LangChain4JVersion))

  def compilationProject(configureFunc: Project => Project): CompositeProject = {
    val pathToSample = "samples"

    new CompositeProject {

      def componentProjects: Seq[Project] = innerProjects :+ root

      lazy val root =
        Project(id = s"samples", base = file(pathToSample))
          .aggregate(innerProjects.map(p => p: ProjectReference): _*)

      import akka.grpc.sbt.AkkaGrpcPlugin.autoImport._
      lazy val innerProjects =
        findSamples
          .map { dir =>
            val proj = Project("sample-" + dir.getName, dir)
              .disablePlugins(HeaderPlugin)
              .enablePlugins(AkkaGrpcPlugin)
              .settings(
                Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "it" / "java",
                akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
                akkaGrpcCodeGeneratorSettings ++= CommonSettings.serviceGrpcGeneratorSettings)

            additionalDeps.get(dir.getName).fold(proj)(deps => proj.settings(libraryDependencies ++= deps))
          }
          .map(configureFunc)

      def findSamples: Seq[File] = {
        file(pathToSample)
          .listFiles()
          .filter { file => file.isDirectory }
      }
    }
  }
}
