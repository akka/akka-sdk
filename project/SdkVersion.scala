import com.github.sbt.git.GitPlugin
import com.github.sbt.git.SbtGit.GitKeys.useConsoleForROGit
import sbt.Keys._
import sbt._
import sbtdynver.DynVerPlugin

/**
 * Version settings — automatically added to all projects.
 */
object SdkVersion extends AutoPlugin {

  override def requires = DynVerPlugin && GitPlugin
  override def trigger = allRequirements

  import DynVerPlugin.autoImport._

  override def buildSettings = versionSettings ++ Seq(
    // JGit cannot read the `.git` file that marks a git worktree, so shell out to git instead
    useConsoleForROGit := (baseDirectory.value / ".git").isFile)

  // project settings, as versions can be different for different projects (sonatype snapshots)
  override def projectSettings = versionSettings

  def versionSettings = Seq(
    version := dynverGitDescribeOutput.value.mkVersion(versionFmt(dynverSonatypeSnapshots.value), "latest"),
    dynver := version.value)

  def versionFmt(sonatypeSnapshots: Boolean)(out: sbtdynver.GitDescribeOutput): String = {
    import scala.sys.process.Process
    // make sure the version doesn't change based on time
    val dirtySuffix = if (out.isDirty()) "-dev" else ""
    val snapshotSuffix = if (sonatypeSnapshots) "-SNAPSHOT" else ""
    val suffix = dirtySuffix + snapshotSuffix
    val tagVersion = out.ref.value.stripPrefix("v")
    if (out.isCleanAfterTag) tagVersion
    else tagVersion + out.commitSuffix.mkString("-", "-", "") + suffix
  }
}
