import akka.grpc.sbt.AkkaGrpcPlugin
import com.github.sbt.JavaFormatterPlugin

import java.io.File
import de.heikoseeberger.sbtheader.HeaderPlugin
import sbt.*
import sbt.CompositeProject
import sbt.Keys.*
import sbt.Project
import sbt.ProjectReference
import sbt.Test

object SamplesCompilationProject {

  private val LangChain4JVersion = "1.1.0"
  private val additionalDeps = Map(
    "spring-dependency-injection" -> Seq("org.springframework" % "spring-context" % "6.2.8"),
    "ask-akka-agent" -> Seq(
      "dev.langchain4j" % "langchain4j-open-ai" % LangChain4JVersion,
      "dev.langchain4j" % "langchain4j" % LangChain4JVersion,
      "dev.langchain4j" % "langchain4j-mongodb-atlas" % "1.1.0-beta7"))

  def compilationProject(configureFunc: Project => Project): CompositeProject = {
    val pathToSample = "samples"

    new CompositeProject {

      def componentProjects: Seq[Project] = innerProjects :+ root

      lazy val root =
        Project(id = s"samples", base = file(pathToSample))
          .aggregate(innerProjects.map(p => p: ProjectReference): _*)

      import akka.grpc.sbt.AkkaGrpcPlugin.autoImport._

      val formatIfNeeded = taskKey[Unit]("Format with prettier-maven-plugin if sources have changed")

      lazy val innerProjects =
        findSamples
          .map { dir =>
            val formatIfNeeded = taskKey[Unit]("Format with prettier-maven-plugin if needed")
            val proj = Project("sample-" + dir.getName, dir)
              // JavaFormatterPlugin must be disabled
              // samples use prettier-maven-plugin from the parent pom
              .disablePlugins(HeaderPlugin, JavaFormatterPlugin)
              .enablePlugins(AkkaGrpcPlugin)
              .settings(
                Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "it" / "java",
                akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
                akkaGrpcCodeGeneratorSettings ++= CommonSettings.serviceGrpcGeneratorSettings,
                // Disable tests for composite projects since they're primarily for compilation verification
                // Maven sets akka.javasdk.dev-mode.project-artifact-id automatically but SBT composite projects don't have this
                Test / test := {},
                Test / testOnly := {},
                // Only run prettier-maven-plugin if there are sources to compile
                formatIfNeeded := {
                  val srcs = (Compile / sources).value
                  val targetDir = (Compile / compile / streams).value.cacheDirectory
                  val markerFile = targetDir / "prettier-last-run"
                  val changed =
                    if (markerFile.exists) {
                      srcs.exists(src => src.lastModified() > markerFile.lastModified())
                    } else {
                      srcs.nonEmpty
                    }
                  if (changed) {
                    println(s"[info] Running: mvn -Pformatting prettier:write in ${baseDirectory.value}")
                    val process = scala.sys.process.Process("mvn -Pformatting prettier:write", baseDirectory.value)
                    val outputLines = process.lineStream_!
                    outputLines.foreach { line =>
                      if (line.contains("[INFO] Reformatted file:")) println(line)
                    }
                    markerFile.createNewFile()
                    markerFile.setLastModified(System.currentTimeMillis())
                  }
                },
                Compile / compile := (Compile / compile).dependsOn(formatIfNeeded).value)

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
