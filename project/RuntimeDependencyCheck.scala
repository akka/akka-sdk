import java.util.Properties
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

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
 * Compares the versions of the SDK direct dependencies against the versions the Akka Runtime provides for the same
 * artifacts, so that a runtime bump moving e.g. jackson or logback does not silently leave the SDK behind.
 *
 * This matters most for user projects: Maven picks the nearest definition, so a stale direct dependency in akka-javasdk
 * downgrades the artifact below what the runtime needs, where sbt would have quietly picked the newer one.
 *
 * The runtime versions come from the `akka-runtime-dependencies` manifest, the same artifact the
 * `akkaRuntimeDependencyCheck` enforcer rule uses in user builds, so both judge alignment by the same numbers. The
 * check here is stricter: the enforcer only fails a user project that resolves a newer version than the runtime, while
 * a direct SDK dependency has to match exactly in either direction.
 */
object RuntimeDependencyCheck {

  /** Artifacts where the SDK deliberately differs from the version the runtime provides. */
  val allowedDivergence: Set[(String, String)] = Set(
    // exclusions can be added here
  )

  private val ManifestArtifactName = "akka-runtime-dependencies"
  private val ManifestResource = "META-INF/akka-runtime-dependencies.properties"

  /** Version literals are only editable where they are written, which is not always Dependencies.scala. */
  private val VersionSources = Seq("project/Dependencies.scala", "project/plugins.sbt")

  final case class Mismatch(organization: String, name: String, declared: String, runtime: String)

  def resolveRuntimeVersions(
      dependencyResolution: DependencyResolution,
      scalaModuleInfo: Option[ScalaModuleInfo],
      log: Logger): Map[(String, String), String] = {
    val manifestArtifact = "io.akka" % ManifestArtifactName % Dependencies.AkkaRuntimeVersion
    val descriptor = dependencyResolution.moduleDescriptor(
      ModuleDescriptorConfiguration(
        "io.akka" % "akka-sdk-runtime-dependency-check" % "0.0.0",
        ModuleInfo("akka-sdk-runtime-dependency-check"))
        .withScalaModuleInfo(scalaModuleInfo)
        .withDependencies(Vector(manifestArtifact)))

    val report = dependencyResolution
      .update(descriptor, UpdateConfiguration().withLogging(UpdateLogging.Quiet), UnresolvedWarningConfiguration(), log)
      .fold(warning => throw warning.resolveException, identity)

    val manifestJar = report.configurations.iterator
      .flatMap(_.modules)
      .flatMap(_.artifacts)
      .collectFirst { case (artifact, file) if artifact.name == ManifestArtifactName => file }
      .getOrElse(sys.error(s"Resolving $manifestArtifact produced no artifact"))

    parseManifest(manifestJar)
  }

  def parseManifest(manifestJar: File): Map[(String, String), String] = {
    val properties = new Properties()
    val zip = new ZipFile(manifestJar)
    try {
      val entry =
        Option(zip.getEntry(ManifestResource)).getOrElse(sys.error(s"$manifestJar does not contain $ManifestResource"))
      val in = zip.getInputStream(entry)
      try properties.load(in)
      finally in.close()
    } finally zip.close()

    // keys are organization%name, with the Scala suffix already applied where there is one
    properties
      .stringPropertyNames()
      .asScala
      .map { key =>
        val separator = key.indexOf('%')
        if (separator == -1)
          sys.error(s"Unexpected entry '$key' in $ManifestResource, expected 'organization%name'")
        (key.substring(0, separator), key.substring(separator + 1)) -> properties.getProperty(key)
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

  /** Where a version literal is written, so the failure can name the file and line to edit. */
  def declarationSites(version: String, buildDirectory: File): Seq[String] = {
    val literal = "\"" + version + "\""
    VersionSources.map(path => path -> (buildDirectory / path)).filter(_._2.isFile).flatMap { case (path, file) =>
      IO.readLines(file).zipWithIndex.collect {
        case (line, index) if line.contains(literal) => s"$path:${index + 1}"
      }
    }
  }

  def render(mismatch: Mismatch, buildDirectory: File): String = {
    val sites = declarationSites(mismatch.declared, buildDirectory)
    val where = if (sites.isEmpty) "" else sites.mkString(" in ", ", ", "")
    s"  ${mismatch.organization}:${mismatch.name} - declared ${mismatch.declared}$where, " +
    s"runtime provides ${mismatch.runtime}"
  }

  def check(
      dependencyResolution: DependencyResolution,
      scalaModuleInfo: Option[ScalaModuleInfo],
      directDependencies: Seq[ModuleID],
      scalaVersion: String,
      scalaBinaryVersion: String,
      buildDirectory: File,
      log: Logger): Unit = {
    val runtimeVersions = resolveRuntimeVersions(dependencyResolution, scalaModuleInfo, log)
    val mismatches = findMismatches(directDependencies, runtimeVersions, scalaVersion, scalaBinaryVersion)

    if (mismatches.nonEmpty)
      throw new MessageOnlyException(
        s"Dependency versions are not aligned with Akka Runtime ${Dependencies.AkkaRuntimeVersion}:\n" +
        mismatches.map(mismatch => render(mismatch, buildDirectory)).mkString("\n") +
        "\nUpdate the version where it is declared, or add the artifact to " +
        "RuntimeDependencyCheck.allowedDivergence if the difference is intentional. A version with no declaration " +
        s"site in ${VersionSources.mkString(" or ")} comes from a plugin, the akka-grpc and protobuf versions for " +
        "example move with the sbt-akka-grpc version in project/plugins.sbt.")
  }
}
