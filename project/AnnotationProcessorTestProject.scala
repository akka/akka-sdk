import java.io.File

import sbt.*
import sbt.CompositeProject
import sbt.Project
import sbt.ProjectReference

object AnnotationProcessorTestProject {

  def compilationProject(configureFunc: Project => Project): CompositeProject = {
    val pathToTests = "akka-javasdk-annotation-processor-tests"

    new CompositeProject {

      def componentProjects: Seq[Project] = innerProjects :+ root

      lazy val root =
        Project(id = pathToTests, base = file(pathToTests))
          .disablePlugins(Publish)
          .aggregate(innerProjects.map(p => p: ProjectReference): _*)

      lazy val innerProjects =
        findProjects
          .map { dir =>
            Project(s"$pathToTests-" + dir.getName, dir)
              .disablePlugins(Publish)
          }
          .map(configureFunc)

      def findProjects: Seq[File] = {
        file(pathToTests)
          .listFiles()
          .filter { file => file.isDirectory && file.getName.endsWith("descriptors") }
      }
    }
  }
}
