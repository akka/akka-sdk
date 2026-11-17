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
    InventoryConfig.parse(resource)
  }

  private def write(folder: Path, name: String, content: String): URL =
    Files.writeString(folder.resolve(name), content, UTF_8).toUri.toURL

  private def applicationConf(content: String): URL =
    write(Files.createTempDirectory("inventory-config"), "application.conf", content)

  private def read(application: URL): List[InventoryGuardrail] = {
    val config = InventoryConfig.merge(Optional.of(InventoryConfig.parse(application)), sdkDefaults)
    GuardrailReader.read(config).asScala.toList
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

      val exception = intercept[InventoryException](read(application))

      exception.getMessage should include("unresolved substitution")
    }

    "follow a relative include" in {
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
      val application = write(folder, "application.conf", "include \"guardrails.conf\"\n")

      read(application).map(_.name()) shouldBe List("refund-limit")
    }

    "report a syntax error with the file" in {
      val application = applicationConf("akka { ")

      val exception = intercept[InventoryException](InventoryConfig.parse(application))

      exception.getMessage should include("application.conf")
    }
  }
}
