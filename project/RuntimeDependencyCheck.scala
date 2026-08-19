import sbt._
import sbt.librarymanagement.CrossVersion
import sbt.librarymanagement.DependencyResolution
import sbt.librarymanagement.ModuleDescriptorConfiguration
import sbt.librarymanagement.ModuleInfo
import sbt.librarymanagement.ScalaModuleInfo
import sbt.librarymanagement.UnresolvedWarningConfiguration
import sbt.librarymanagement.UpdateConfiguration
import sbt.librarymanagement.UpdateLogging
import sbt.util.Logger

/**
 * Compares the versions declared in [[Dependencies]] against the versions the Akka Runtime resolves for the same
 * artifacts, so that a runtime bump moving e.g. jackson or logback does not silently leave the SDK behind.
 *
 * This matters most for user projects: Maven picks the nearest definition, so a stale direct dependency in akka-javasdk
 * downgrades the artifact below what the runtime needs, where sbt would have quietly picked the newer one.
 */
object RuntimeDependencyCheck {

  /** Artifacts where the SDK deliberately differs from the version the runtime resolves. */
  val allowedDivergence: Set[(String, String)] = Set(
    // exclusions can be added here
  )

  final case class Mismatch(organization: String, name: String, declared: String, runtime: String) {
    def render: String = {
      val constants = Dependencies.runtimeAlignedVersions.collect { case (constant, `declared`) => constant }
      val where = if (constants.isEmpty) "" else constants.toSeq.sorted.mkString(" (", ", ", ")")
      s"  $organization:$name - declared $declared$where, runtime brings $runtime"
    }
  }

  /**
   * Resolves the runtime on its own. Reading the versions off our own projects would not work, conflict resolution has
   * already picked the highest version and hides that we asked for a lower one.
   */
  def resolveRuntimeVersions(
      dependencyResolution: DependencyResolution,
      scalaModuleInfo: Option[ScalaModuleInfo],
      log: Logger): Map[(String, String), String] = {
    val descriptor = dependencyResolution.moduleDescriptor(
      ModuleDescriptorConfiguration(
        "io.akka" % "akka-sdk-runtime-dependency-check" % "0.0.0",
        ModuleInfo("akka-sdk-runtime-dependency-check"))
        .withScalaModuleInfo(scalaModuleInfo)
        .withDependencies(Vector(Dependencies.AkkaDevRuntime, Dependencies.akkaSdkSpi)))

    val report = dependencyResolution
      .update(descriptor, UpdateConfiguration().withLogging(UpdateLogging.Quiet), UnresolvedWarningConfiguration(), log)
      .fold(warning => throw warning.resolveException, identity)

    val configurationReport =
      report.configurations
        .find(_.configuration.name == "compile")
        .orElse(report.configurations.headOption)
        .getOrElse(sys.error("Akka Runtime resolution produced no configuration report"))

    configurationReport.modules
      .filterNot(_.evicted)
      .map { module =>
        (module.module.organization, module.module.name) -> module.module.revision
      }
      .toMap
  }

  def findMismatches(
      directDependencies: Seq[ModuleID],
      runtimeVersions: Map[(String, String), String],
      scalaVersion: String,
      scalaBinaryVersion: String): Seq[Mismatch] =
    directDependencies
      .flatMap { module =>
        val name =
          CrossVersion(module.crossVersion, scalaVersion, scalaBinaryVersion).fold(module.name)(mangle =>
            mangle(module.name))
        val key = (module.organization, name)
        if (allowedDivergence(key)) None
        else
          runtimeVersions.get(key).filter(_ != module.revision).map { runtimeRevision =>
            Mismatch(module.organization, name, module.revision, runtimeRevision)
          }
      }
      .distinct
      .sortBy(mismatch => (mismatch.organization, mismatch.name))

  def check(
      dependencyResolution: DependencyResolution,
      scalaModuleInfo: Option[ScalaModuleInfo],
      directDependencies: Seq[ModuleID],
      scalaVersion: String,
      scalaBinaryVersion: String,
      log: Logger): Unit = {
    val runtimeVersions = resolveRuntimeVersions(dependencyResolution, scalaModuleInfo, log)
    val mismatches = findMismatches(directDependencies, runtimeVersions, scalaVersion, scalaBinaryVersion)

    if (mismatches.nonEmpty)
      throw new MessageOnlyException(
        s"Dependency versions are not aligned with Akka Runtime ${Dependencies.AkkaRuntimeVersion}:\n" +
        mismatches.map(_.render).mkString("\n") +
        "\nUpdate the versions in project/Dependencies.scala, or add the artifact to " +
        "RuntimeDependencyCheck.allowedDivergence if the difference is intentional.")
  }
}
