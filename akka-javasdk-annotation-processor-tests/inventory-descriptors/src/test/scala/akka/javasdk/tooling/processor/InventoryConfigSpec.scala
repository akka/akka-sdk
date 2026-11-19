/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import scala.jdk.CollectionConverters._
import scala.util.Using

import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryConfigSpec extends AnyWordSpec with Matchers {

  private val RefundLimit =
    """
      |akka.javasdk.agent.guardrails.refund-limit {
      |  class = "com.example.RefundLimitGuard"
      |  category = POLICY
      |  agents = ["*"]
      |  report-only = false
      |}
      |""".stripMargin

  private val ReportOnly = "akka.javasdk.agent.guardrails.refund-limit.report-only = true\n"

  private val TestClasspath: Seq[Path] =
    System.getProperty("java.class.path").split(File.pathSeparator).toSeq.map(Path.of(_))

  private def write(folder: Path, name: String, content: String): Path =
    Files.writeString(folder.resolve(name), content, UTF_8)

  private def applicationConf(content: String): Path = {
    val folder = Files.createTempDirectory("inventory-config")
    write(folder, "application.conf", content)
    folder
  }

  private def jar(entries: (String, String)*): Path = {
    val file = Files.createTempFile("inventory-config", ".jar")
    Using.resource(new ZipOutputStream(Files.newOutputStream(file))) { zip =>
      entries.foreach { case (name, content) =>
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes(UTF_8))
        zip.closeEntry()
      }
    }
    file
  }

  private def load(application: Path, classpath: Path*): Config =
    Using.resource(InventoryClasspath.classLoader(Optional.of(application), (classpath ++ TestClasspath).asJava)) {
      loader => InventoryConfig.load(loader)
    }

  private def read(application: Path, classpath: Path*): List[InventoryGuardrail] =
    GuardrailReader.read(load(application, classpath: _*), _ => ()).asScala.toList

  "InventoryConfig" should {

    "layer application.conf over the SDK defaults" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails."default model-call jailbreak".agents = ["*"]
        |""".stripMargin)

      val guardrails = read(application)

      guardrails should have size 1
      val guardrail = guardrails.head
      guardrail.name() shouldBe "default model-call jailbreak"
      guardrail.className() shouldBe "akka.javasdk.agent.ModelCallSimilarityGuard"
      guardrail.category() shouldBe "JAILBREAK"
      guardrail.agents() shouldBe java.util.List.of("*")
    }

    "ignore an unresolved value outside the guardrails" in {
      val application = applicationConf("""
        |akka.javasdk.agent.openai.api-key = ${OPENAI_API_KEY}
        |""".stripMargin)

      read(application) shouldBe empty
    }

    "ignore env vars" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.env {
        |  class = "com.example.EnvGuard"
        |  category = ${PATH}
        |  agents = ["*"]
        |}
        |""".stripMargin)

      read(application).head.unknownKeys() shouldBe java.util.List.of("category")
    }

    "record required keys from an optional substitution as unknown" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = ${?GUARD_CLASS}
        |  category = ${?GUARD_CATEGORY}
        |  agents = ["support"]
        |}
        |""".stripMargin)

      read(application).head.unknownKeys() shouldBe java.util.List.of("category", "class")
    }

    "list a guardrail with agents from an optional substitution as unknown" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = "com.example.RefundLimitGuard"
        |  category = POLICY
        |  agents = ${?GUARD_AGENTS}
        |}
        |""".stripMargin)

      read(application).head.unknownKeys() shouldBe java.util.List.of("agents")
    }

    "record a default with an optional override as unknown" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = "com.example.RefundLimitGuard"
        |  category = POLICY
        |  category = ${?GUARD_CATEGORY}
        |  report-only = false
        |  report-only = ${?GUARD_REPORT_ONLY}
        |  agents = ["support"]
        |}
        |""".stripMargin)

      read(application).head.unknownKeys() shouldBe java.util.List.of("category", "report-only")
    }

    "record an SDK default with an optional override as unknown" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails."default model-call jailbreak".agents = ${?GUARD_AGENTS}
        |""".stripMargin)

      val guardrail = read(application).head

      guardrail.name() shouldBe "default model-call jailbreak"
      guardrail.className() shouldBe "akka.javasdk.agent.ModelCallSimilarityGuard"
      guardrail.unknownKeys() shouldBe java.util.List.of("agents")
    }

    "record a reference to a key with an optional override as unknown" in {
      val application = applicationConf("""
        |guard-category = POLICY
        |guard-category = ${?GUARD_CATEGORY}
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = "com.example.RefundLimitGuard"
        |  category = ${guard-category}
        |  agents = ["support"]
        |}
        |""".stripMargin)

      read(application).head.unknownKeys() shouldBe java.util.List.of("category")
    }

    "list a guardrail based on one with an optional override as unknown" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.base {
        |  class = ${?GUARD_CLASS}
        |  category = POLICY
        |  agents = ["support"]
        |}
        |akka.javasdk.agent.guardrails.derived = ${akka.javasdk.agent.guardrails.base} { agents = ["sales"] }
        |""".stripMargin)

      val derived = read(application).find(_.name() == "derived").get

      (derived.unknownKeys() should contain).allOf("agents", "category", "class")
    }

    "keep values that do not depend on an optional substitution without a value" in {
      val application = applicationConf("""
        |shared-agents = ["support"]
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = "com.example.RefundLimitGuard"
        |  category = POLICY
        |  agents = ${?shared-agents}
        |  tools += "issueRefund"
        |}
        |""".stripMargin)

      val guardrail = read(application).head

      guardrail.agents() shouldBe java.util.List.of("support")
      guardrail.tools() shouldBe java.util.List.of("issueRefund")
      guardrail.unknownKeys() shouldBe empty
    }

    "read substitution text inside a quoted string as a literal" in {
      val application = applicationConf("""
        |akka.javasdk.agent.guardrails.refund-limit {
        |  class = "com.example.RefundLimitGuard"
        |  category = "price ${?NOT_A_SUB}"
        |  control-id = "price ${..}"
        |  agents = ["support"]
        |}
        |""".stripMargin)

      val guardrail = read(application).head

      guardrail.category() shouldBe "price ${?NOT_A_SUB}"
      guardrail.controlId() shouldBe java.util.Optional.of("price ${..}")
      guardrail.unknownKeys() shouldBe empty
    }

    "apply an include after a literal over the literal" in {
      val application = applicationConf(RefundLimit + "include \"override\"\n")
      write(application, "override.conf", ReportOnly)

      read(application).head.reportOnly() shouldBe true
    }

    "apply a literal after an include over the include" in {
      val application = applicationConf("include \"override\"\n" + RefundLimit)
      write(application, "override.conf", ReportOnly)

      read(application).head.reportOnly() shouldBe false
    }

    "apply a later include over an earlier include" in {
      val application = applicationConf(RefundLimit + "include \"first\"\ninclude \"second\"\n")
      write(application, "first.conf", ReportOnly)
      write(application, "second.conf", "akka.javasdk.agent.guardrails.refund-limit.report-only = false\n")

      read(application).head.reportOnly() shouldBe false
    }

    "read an include inside a block under the path of the block" in {
      val application =
        applicationConf(RefundLimit + "akka.javasdk.agent.guardrails.refund-limit { include \"nested\" }\n")
      write(application, "nested.conf", "report-only = true\n")

      read(application).head.reportOnly() shouldBe true
    }

    "read an include relative to the file that holds it" in {
      val application = applicationConf(RefundLimit + "include \"shared/first\"\n")
      Files.createDirectory(application.resolve("shared"))
      write(application, "shared/first.conf", "include \"second\"\n")
      write(application, "shared/second.conf", ReportOnly)

      read(application).head.reportOnly() shouldBe true
    }

    "record an optional substitution in an included file as unknown" in {
      val application = applicationConf(RefundLimit + "include \"override\"\n")
      write(application, "override.conf", "akka.javasdk.agent.guardrails.refund-limit.report-only = ${?REPORT_ONLY}\n")

      read(application).head.unknownKeys() shouldBe java.util.List.of("report-only")
    }

    "read an include from a dependency jar" in {
      val application = applicationConf(RefundLimit + "include classpath(\"library.conf\")\n")

      read(application, jar("library.conf" -> ReportOnly)).head.reportOnly() shouldBe true
    }

    "merge an include with the same name from every dependency jar" in {
      val application = applicationConf(RefundLimit + "include \"library\"\n")
      val first = jar("library.conf" -> ReportOnly)
      val second = jar("library.conf" -> "akka.javasdk.agent.guardrails.refund-limit.control-id = CTRL-7\n")

      val guardrail = read(application, first, second).head

      guardrail.reportOnly() shouldBe true
      guardrail.controlId() shouldBe java.util.Optional.of("CTRL-7")
    }

    "read the output folder once when the classpath lists it" in {
      val application =
        applicationConf(RefundLimit + "akka.javasdk.agent.guardrails.refund-limit.tools += \"refund\"\n")

      read(application, application).head.tools() shouldBe java.util.List.of("refund")
    }

    "ignore a missing optional include" in {
      val application = applicationConf(RefundLimit + "include \"missing\"\n")

      read(application) should have size 1
    }

    "report a missing required include" in {
      val application = applicationConf(RefundLimit + "include required(\"missing\")\n")

      val exception = intercept[InventoryException](load(application))

      exception.getMessage should include("missing")
    }

    "reject file and URL includes" in {
      val statements = List(
        "include file(\"/etc/guardrails.conf\")",
        "include required(file(\"/etc/guardrails.conf\"))",
        "include url(\"http://localhost:1/guardrails.conf\")",
        "include required(url(\"http://localhost:1/guardrails.conf\"))",
        "include \"http://localhost:1/guardrails.conf\"",
        "include \"file:/etc/guardrails.conf\"")

      statements.foreach { statement =>
        val exception = intercept[InventoryException](load(applicationConf(RefundLimit + statement + "\n")))

        withClue(statement) {
          exception.getMessage should include("The service inventory does not support include")
        }
      }
    }

    "reject a file include inside an included file" in {
      val application = applicationConf(RefundLimit + "include \"shared\"\n")
      write(application, "shared.conf", "include file(\"/etc/guardrails.conf\")\n")

      val exception = intercept[InventoryException](load(application))

      exception.getMessage should include("/etc/guardrails.conf")
    }

    "read a guardrail from a reference.conf in a dependency jar" in {
      val application = applicationConf("")

      val guardrails = read(application, jar("reference.conf" -> RefundLimit))

      guardrails.map(_.name()) shouldBe List("refund-limit")
    }

    "record an optional substitution in a dependency reference.conf as unknown" in {
      val application = applicationConf("")
      val reference = jar("reference.conf" -> (RefundLimit + """
          |akka.javasdk.agent.guardrails.refund-limit.report-only = ${?GUARD_REPORT_ONLY}
          |""".stripMargin))

      read(application, reference).head.unknownKeys() shouldBe java.util.List.of("report-only")
    }

    "keep a value from application.conf over an optional substitution in reference.conf" in {
      val application = applicationConf(ReportOnly)
      val reference = jar("reference.conf" -> (RefundLimit + """
          |akka.javasdk.agent.guardrails.refund-limit.report-only = ${?GUARD_REPORT_ONLY}
          |""".stripMargin))

      val guardrail = read(application, reference).head

      guardrail.reportOnly() shouldBe true
      guardrail.unknownKeys() shouldBe empty
    }

    "resolve a reference.conf substitution against application.conf" in {
      val application = applicationConf("library.guard-class = \"com.example.FromApplication\"\n")
      val reference = jar("reference.conf" -> (RefundLimit + """
          |library.guard-class = "com.example.FromReference"
          |akka.javasdk.agent.guardrails.refund-limit.class = ${library.guard-class}
          |""".stripMargin))

      read(application, reference).head.className() shouldBe "com.example.FromApplication"
    }

    "list a reference.conf guardrail that application.conf enables through a substitution" in {
      val application = applicationConf("library.guard-agents = [\"*\"]\n")
      val reference = jar("reference.conf" -> """
          |library.guard-agents = []
          |akka.javasdk.agent.guardrails.library-guard {
          |  class = "com.example.LibraryGuard"
          |  category = POLICY
          |  agents = ${library.guard-agents}
          |}
          |""".stripMargin)

      read(application, reference).map(_.name()) shouldBe List("library-guard")
    }

    "report a syntax error with the file" in {
      val application = applicationConf("akka { ")

      val exception = intercept[InventoryException](load(application))

      exception.getMessage should include("application.conf")
    }
  }
}
