/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import java.io.ByteArrayInputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xml.sax.InputSource

class RuntimeVersionCheckSpec extends AnyWordSpec with Matchers {
  import RuntimeVersionCheck._

  private def parse(xml: String) =
    DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(new InputSource(new ByteArrayInputStream(xml.getBytes)))

  "RuntimeVersionCheck.isOlder" should {
    "flag when the current version is older" in {
      isOlder("1.6.16", "1.6.17") shouldBe true
      isOlder("1.5.42", "1.6.1") shouldBe true
      isOlder("1.6.2", "1.6.17") shouldBe true
    }
    "not flag when up to date or newer" in {
      isOlder("1.6.17", "1.6.17") shouldBe false
      isOlder("1.6.18", "1.6.17") shouldBe false
    }
    "handle differing segment counts" in {
      isOlder("1.6", "1.6.1") shouldBe true
      isOlder("1.6.0", "1.6") shouldBe false
    }
  }

  "RuntimeVersionCheck.parseRemoteRepositoryIds" should {
    "prefer ids recorded against the .jar entry, deduplicated, in file order" in {
      val content =
        """#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.
          |#Mon Sep 21 16:12:44 EDT 2026
          |akka-runtime-core_2.13-1.6.13.jar>akka-plugin-repository=
          |akka-runtime-core_2.13-1.6.13.jar>akka-repository=
          |akka-runtime-core_2.13-1.6.13.pom>akka-repository=
          |""".stripMargin
      parseRemoteRepositoryIds(content) shouldBe Seq("akka-plugin-repository", "akka-repository")
    }
    "fall back to .pom ids when no .jar id is recorded" in {
      val content = "akka-runtime-core_2.13-1.6.13.pom>akka-repository=\n"
      parseRemoteRepositoryIds(content) shouldBe Seq("akka-repository")
    }
    "return nothing for empty or comment-only content" in {
      parseRemoteRepositoryIds("# just a comment\n") shouldBe empty
    }
  }

  "RuntimeVersionCheck.resolveLocalRepository" should {
    "default to ~/.m2/repository when unset" in {
      val doc = parse("<settings></settings>")
      resolveLocalRepository(doc) shouldBe new File(System.getProperty("user.home"), ".m2/repository")
    }
    "use an explicit <localRepository>" in {
      val doc = parse("<settings><localRepository>/custom/repo</localRepository></settings>")
      resolveLocalRepository(doc) shouldBe new File("/custom/repo")
    }
  }

  "RuntimeVersionCheck.buildRepositoryIndex" should {
    "index mirrors, profile repositories and pluginRepositories, with matching server credentials" in {
      val settingsXml =
        """<settings>
          |  <servers>
          |    <server>
          |      <id>akka-repository</id>
          |      <username>bob</username>
          |      <password>secret</password>
          |    </server>
          |    <server>
          |      <id>encrypted-repo</id>
          |      <username>alice</username>
          |      <password>{encryptedBlob}</password>
          |    </server>
          |  </servers>
          |  <mirrors>
          |    <mirror>
          |      <id>company-artifactory</id>
          |      <url>https://artifactory.example.com/maven-remote-akka</url>
          |      <mirrorOf>*</mirrorOf>
          |    </mirror>
          |  </mirrors>
          |  <profiles>
          |    <profile>
          |      <repositories>
          |        <repository>
          |          <id>akka-repository</id>
          |          <url>https://repo.akka.io/TOKEN/secure</url>
          |        </repository>
          |      </repositories>
          |      <pluginRepositories>
          |        <pluginRepository>
          |          <id>akka-plugin-repository</id>
          |          <url>https://repo.akka.io/TOKEN/secure</url>
          |        </pluginRepository>
          |      </pluginRepositories>
          |    </profile>
          |  </profiles>
          |</settings>
          |""".stripMargin
      val index = buildRepositoryIndex(parse(settingsXml))

      index("akka-repository") shouldBe RuntimeVersionCheck.RepoLocation(
        "https://repo.akka.io/TOKEN/secure",
        Some("bob"),
        Some("secret"))
      index("akka-plugin-repository").baseUrl shouldBe "https://repo.akka.io/TOKEN/secure"
      index("company-artifactory").baseUrl shouldBe "https://artifactory.example.com/maven-remote-akka"
      // encrypted passwords are never picked up
      index.get("encrypted-repo") shouldBe None
    }
  }

  "RuntimeVersionCheck.resolveCandidateLocations" should {
    "prefer the repository the artifact was actually resolved from, then repo.akka.io as a fallback" in {
      val index = Map(
        "company-artifactory" -> RepoLocation("https://artifactory.example.com/maven-remote-akka", None, None),
        "akka-repository" -> RepoLocation("https://repo.akka.io/TOKEN/secure", None, None))

      withTempRemoteRepositories("company-artifactory") { localRepo =>
        resolveCandidateLocations(index, localRepo, "1.6.17").map(_._1) shouldBe Seq(
          "company-artifactory",
          "akka-repository")
      }
    }
    "fall back to repo.akka.io alone when nothing was resolved locally" in {
      val index = Map("akka-repository" -> RepoLocation("https://repo.akka.io/TOKEN/secure", None, None))
      val emptyLocalRepo = new File(System.getProperty("java.io.tmpdir"), "no-such-repo-" + System.nanoTime())
      resolveCandidateLocations(index, emptyLocalRepo, "1.6.17").map(_._1) shouldBe Seq("akka-repository")
    }
  }

  private def withTempRemoteRepositories(repositoryId: String)(test: File => Unit): Unit = {
    val localRepo = Files_createTempDir()
    try {
      val artifactDir = new File(localRepo, "io/akka/akka-runtime-core_2.13/1.6.17")
      artifactDir.mkdirs()
      val marker = new File(artifactDir, "_remote.repositories")
      java.nio.file.Files.write(marker.toPath, s"akka-runtime-core_2.13-1.6.17.jar>$repositoryId=\n".getBytes)
      test(localRepo)
    } finally deleteRecursively(localRepo)
  }

  private def Files_createTempDir(): File = {
    val dir = java.nio.file.Files.createTempDirectory("runtime-version-check-spec").toFile
    dir
  }

  private def deleteRecursively(file: File): Unit = {
    if (file.isDirectory) file.listFiles().foreach(deleteRecursively)
    file.delete()
  }
}
