import java.io.File

import de.heikoseeberger.sbtheader.HeaderPlugin
import sbt.*
import sbt.CompositeProject
import sbt.Keys.*
import sbt.Project
import sbt.ProjectReference
import sbt.Test

object SamplesCompilationProject {

  def compilationProject(configureFunc: Project => Project): CompositeProject = {
    val pathToSample = "samples"

    new CompositeProject {

      def componentProjects: Seq[Project] = innerProjects :+ root

      lazy val root =
        Project(id = s"samples", base = file(pathToSample))
          .aggregate(innerProjects.map(p => p: ProjectReference): _*)

      lazy val innerProjects =
        findSamples
          .map { dir =>
            Project(dir.getName, dir)
              .disablePlugins(HeaderPlugin)
              .settings(Test / unmanagedSourceDirectories += baseDirectory.value / "src" / "it" / "java")
          }
          .map(configureFunc)

      def findSamples: Seq[File] = {
        file(pathToSample)
          .listFiles()
          .filter { file =>
            file.isDirectory && file.getName.startsWith("java-")
          }
      }
    }
  }
}
