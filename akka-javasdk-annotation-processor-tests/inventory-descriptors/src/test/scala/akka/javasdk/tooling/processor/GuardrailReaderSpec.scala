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
    GuardrailReader.read(config, _ => ()).asScala.toList

  private def readWithWarnings(config: Config): (List[InventoryGuardrail], List[String]) = {
    val warnings = List.newBuilder[String]
    val guardrails = GuardrailReader.read(config, warning => warnings += warning).asScala.toList
    (guardrails, warnings.result())
  }

  private def unresolved(hocon: String): Config =
    ConfigFactory.parseString(hocon).resolve(ConfigResolveOptions.noSystem().setAllowUnresolved(true))

  private def failure(config: Config): String =
    intercept[InventoryException](GuardrailReader.read(config, _ => ())).getMessage

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
          Optional.of("CTRL-7"),
          java.util.List.of()))
    }

    "drop duplicate list entries" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = [b, a, b]
        |    agent-roles = [r, r]
        |    tools = [t, t]
        |  }
        |}
        |""".stripMargin)

      val guardrail = read(config).head

      guardrail.agents() shouldBe java.util.List.of("a", "b")
      guardrail.agentRoles() shouldBe java.util.List.of("r")
      guardrail.tools() shouldBe java.util.List.of("t")
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
          Optional.empty(),
          java.util.List.of()))
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

    "fail on a blank class" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = " "
        |    category = POLICY
        |    agents = ["*"]
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("blank value [class]")
    }

    "fail on a control-id that is not a string" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ["*"]
        |    control-id = 42
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("wrong type, expected a string [control-id]")
    }

    "fail on a blank control-id" in {
      val config = parse("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ["*"]
        |    control-id = ""
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("blank value [control-id]")
    }

    "fail on a control-id longer than 256 characters" in {
      val config = parse(s"""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ["*"]
        |    control-id = "${"x" * 257}"
        |  }
        |}
        |""".stripMargin)

      val message = failure(config)

      message should include("refund-limit")
      message should include("value longer than 256 characters [control-id]")
    }

    "accept a control-id of 256 characters" in {
      val config = parse(s"""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ["*"]
        |    control-id = "${"x" * 256}"
        |  }
        |}
        |""".stripMargin)

      read(config).head.controlId() shouldBe Optional.of("x" * 256)
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

    "record unresolved values as unknown and warn for each" in {
      val config = unresolved("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = ${GUARDRAIL_CLASS}
        |    category = POLICY
        |    report-only = ${GUARDRAIL_REPORT_ONLY}
        |    fail-mode = ${GUARDRAIL_FAIL_MODE}
        |    agents = ["*"]
        |    tools = ${GUARDRAIL_TOOLS}
        |    control-id = ${GUARDRAIL_CONTROL_ID}
        |  }
        |}
        |""".stripMargin)

      val (guardrails, warnings) = readWithWarnings(config)

      val guardrail = guardrails.head
      guardrail.unknownKeys() shouldBe java.util.List.of("class", "control-id", "fail-mode", "report-only", "tools")
      guardrail.category() shouldBe "POLICY"
      guardrail.agents() shouldBe java.util.List.of("*")
      warnings should have size 5
      warnings.head should startWith("The service inventory lists [class] of guardrail [refund-limit] as unknown.")
    }

    "list a guardrail with unresolved selectors" in {
      val config = unresolved("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit {
        |    class = "com.example.RefundLimitGuard"
        |    category = POLICY
        |    agents = ${GUARDRAIL_AGENTS}
        |    agent-roles = ${GUARDRAIL_AGENT_ROLES}
        |  }
        |}
        |""".stripMargin)

      val (guardrails, warnings) = readWithWarnings(config)

      guardrails.map(_.name()) shouldBe List("refund-limit")
      guardrails.head.unknownKeys() shouldBe java.util.List.of("agent-roles", "agents")
      warnings should have size 2
    }

    "list an unresolved guardrail entry with every field unknown" in {
      val config = unresolved("""
        |akka.javasdk.agent.guardrails {
        |  refund-limit = ${REFUND_GUARD}
        |}
        |""".stripMargin)

      val (guardrails, warnings) = readWithWarnings(config)

      guardrails.map(_.name()) shouldBe List("refund-limit")
      guardrails.head.unknownKeys() shouldBe java.util.List.of(
        "agent-roles",
        "agents",
        "category",
        "class",
        "control-id",
        "fail-mode",
        "report-only",
        "tools")
      warnings should have size 8
      warnings.foreach(_ should include("of guardrail [refund-limit] as unknown"))
    }
  }
}
