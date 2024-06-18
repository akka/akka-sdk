import java.io.File

import sbt.*
import sbt.CompositeProject
import sbt.Project
import sbt.ProjectReference

object AnnotationProcessorTestProject {

  def compilationProject(configureFunc: Project => Project): CompositeProject = {
    val pathToTests = "annotation-processor-tests"

    new CompositeProject {

      def componentProjects: Seq[Project] = innerProjects :+ root

      lazy val root =
        Project(id = s"javaSdkAnnotationProcessorTests", base = file(pathToTests))
          .disablePlugins(Publish)
          .aggregate(innerProjects.map(p => p: ProjectReference): _*)

      lazy val innerProjects =
        findProjects
          .map { dir =>
            Project("test-ann-proc-" + dir.getName, dir)
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
