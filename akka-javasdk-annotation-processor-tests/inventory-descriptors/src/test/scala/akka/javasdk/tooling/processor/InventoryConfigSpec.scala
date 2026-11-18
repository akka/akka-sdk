/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor

import java.net.URL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InventoryConfigSpec extends AnyWordSpec with Matchers {

  private def sdkDefaults: Config = {
    val resource = getClass.getClassLoader.getResource("akka/javasdk/governance.conf")
    require(resource != null, "Resource not found: akka/javasdk/governance.conf")
    InventoryConfig.parse(resource, _ => ())
  }

  private def write(folder: Path, name: String, content: String): URL =
    Files.writeString(folder.resolve(name), content, UTF_8).toUri.toURL

  private def applicationConf(content: String): URL =
    write(Files.createTempDirectory("inventory-config"), "application.conf", content)

  private def read(application: URL): List[InventoryGuardrail] = {
    val config = InventoryConfig.merge(Optional.of(InventoryConfig.parse(application, _ => ())), sdkDefaults)
    GuardrailReader.read(config, _ => ()).asScala.toList
  }

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

    "skip and report each include" in {
      val folder = Files.createTempDirectory("inventory-config")
      write(
        folder,
        "guardrails.conf",
        """
          |akka.javasdk.agent.guardrails.refund-limit {
          |  class = "com.example.RefundLimitGuard"
          |  category = POLICY
          |  agents = ["*"]
          |}
          |""".stripMargin)
      val application = write(
        folder,
        "application.conf",
        """
          |include "guardrails.conf"
          |include classpath("shared.conf")
          |include required(file("local.conf"))
          |include url("http://example.com/remote.conf")
          |""".stripMargin)
      val includes = List.newBuilder[String]

      val config = InventoryConfig.parse(application, include => includes += include)

      config.isEmpty shouldBe true
      includes.result() shouldBe List(
        "\"guardrails.conf\"",
        "classpath(\"shared.conf\")",
        "file(\"local.conf\")",
        "url(\"http://example.com/remote.conf\")")
    }

    "report a syntax error with the reader description" in {
      val exception = intercept[InventoryException](
        InventoryConfig.parse(new java.io.StringReader("akka { "), "akka/javasdk/governance.conf", _ => ()))

      exception.getMessage should include("akka/javasdk/governance.conf")
    }

    "report a syntax error with the file" in {
      val application = applicationConf("akka { ")

      val exception = intercept[InventoryException](InventoryConfig.parse(application, _ => ()))

      exception.getMessage should include("application.conf")
    }
  }
}
