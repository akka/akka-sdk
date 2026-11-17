/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.tooling.processor

import java.util.Optional

import scala.jdk.CollectionConverters._

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GuardrailReaderSpec extends AnyWordSpec with Matchers {

  private def parse(hocon: String): Config =
    ConfigFactory.parseString(hocon).resolve()

  private def read(config: Config): List[InventoryGuardrail] =
    GuardrailReader.read(config).asScala.toList

  private def failure(config: Config): String =
    intercept[InventoryException](GuardrailReader.read(config)).getMessage

  "GuardrailReader" should {

    "read all fields" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    report-only = true
        |    fail-mode = FAIL-OPEN
        |    agents = [b, a]
        |    agent-roles = [r]
        |    tools = [t2, t1]
        |    control-id = "CTRL-7"
        |  }
        |}
        |""".stripMargin)

      read(config) shouldBe List(
        new InventoryGuardrail(
          "refund-limit",
          "com.example.RefundLimitGuard",
          "POLICY",
          true,
          "fail-open",
          java.util.List.of("a", "b"),
          java.util.List.of("r"),
          java.util.List.of("t1", "t2"),
          Optional.of("CTRL-7")))
    }

    "apply the defaults" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      read(config) shouldBe List(
        new InventoryGuardrail(
          "refund-limit",
          "com.example.RefundLimitGuard",
          "POLICY",
          false,
          "fail-closed",
          java.util.List.of("*"),
          java.util.List.of(),
          java.util.List.of(),
          Optional.empty()))
    }

    "leave out disabled guardrails" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  empty-lists {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = []
        |    agent-roles = []
        |  }
        |  no-lists {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |  }
        |}
        |""".stripMargin)

      read(config) shouldBe empty
    }

    "list a guardrail enabled by roles only" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agent-roles = ["*"]
        |  }
        |}
        |""".stripMargin)

      val guardrails = read(config)

      guardrails should have size 1
      guardrails.head.agents() shouldBe java.util.List.of()
      guardrails.head.agentRoles() shouldBe java.util.List.of("*")
    }

    "sort by name" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  b {
        |    class = "com.example.B"
        |    category = POLICY
        |    agents = ["*"]
        |  }
        |  a {
        |    class = "com.example.A"
        |    category = POLICY
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      read(config).map(_.name()) shouldBe List("a", "b")
    }

    "return no guardrails without the section" in {
      read(ConfigFactory.empty()) shouldBe empty
    }

    "fail on a missing class" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    category = POLICY
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("class")
    }

    "fail on a missing category" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("category")
    }

    "fail on an unknown fail-mode" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    fail-mode = sometimes
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("fail-mode")
      message should include("sometimes")
    }

    "fail on an unresolved recorded value" in {
      val config = ConfigFactory
        .parseString("""
          |akka.javasdk.agent.guardrails {
          |  refund-limit {
          |    class = "com.example.RefundLimitGuard"
          |    category = POLICY
          |    agents = ${GUARDRAIL_AGENTS}
          |  }
          |}
          |""".stripMargin)
        .resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true))

      val message = failure(config)

      message should include("refund-limit")
      message should include("agents")
    }
  }
}
