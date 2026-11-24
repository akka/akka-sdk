/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent

import java.time.Duration
import java.util.Set

import akka.javasdk.agent.RemoteA2aAgent
import akka.javasdk.impl.agent.GuardrailProvider.AgentGuardrails
import io.opentelemetry.api.OpenTelemetry
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AgentImplA2aAgentsSpec extends AnyWordSpec with Matchers {

  private val noGuardrails = new AgentGuardrails(Seq.empty, () => OpenTelemetry.noop().getTracer("test"))

  private def descriptor(agent: RemoteA2aAgent) =
    AgentImpl.toSpiA2aAgents(Seq(agent), noGuardrails).head

  "AgentImpl.toSpiA2aAgents" should {

    "pass on the logical name" in {
      descriptor(RemoteA2aAgent.named("claims")).name shouldBe "claims"
    }

    "allow all skills and use the configured timeout by default" in {
      val d = descriptor(RemoteA2aAgent.named("claims"))
      d.skillFilter("anything") shouldBe true
      d.toolTimeout shouldBe None
    }

    "keep only the skills of a filter" in {
      val d = descriptor(RemoteA2aAgent.named("claims").withSkillFilter(_.startsWith("lookup")))
      d.skillFilter("lookup-claim") shouldBe true
      d.skillFilter("delete-claim") shouldBe false
    }

    "keep only the allowed skills, with a set or with varargs" in {
      val fromSet = descriptor(RemoteA2aAgent.named("claims").withAllowedSkills(Set.of("lookup")))
      fromSet.skillFilter("lookup") shouldBe true
      fromSet.skillFilter("other") shouldBe false
      val fromVarargs = descriptor(RemoteA2aAgent.named("claims").withAllowedSkills("a", "b"))
      fromVarargs.skillFilter("b") shouldBe true
      fromVarargs.skillFilter("c") shouldBe false
    }

    "let the last of filter and allowed skills win" in {
      val d = descriptor(RemoteA2aAgent.named("claims").withAllowedSkills("a").withSkillFilter(_ => true))
      d.skillFilter("other") shouldBe true
    }

    "pass on the timeout" in {
      descriptor(RemoteA2aAgent.named("claims").withTimeout(Duration.ofSeconds(5))).toolTimeout
        .map(_.toSeconds) shouldBe
      Some(5)
    }

    "look up the tool-call guardrails of a skill" in {
      descriptor(RemoteA2aAgent.named("claims")).toolCallGuardrails("lookup") shouldBe empty
    }

    "keep the order of the agents" in {
      val names = AgentImpl
        .toSpiA2aAgents(Seq(RemoteA2aAgent.named("b"), RemoteA2aAgent.named("a")), noGuardrails)
        .map(_.name)
      names shouldBe Seq("b", "a")
    }

    "reject the same agent name twice" in {
      val e = the[IllegalArgumentException] thrownBy AgentImpl.toSpiA2aAgents(
        Seq(RemoteA2aAgent.named("claims"), RemoteA2aAgent.named("claims").withAllowedSkills("a")),
        noGuardrails)
      e.getMessage should include("claims")
    }
  }

  "RemoteA2aAgent.named" should {
    "reject an empty name" in {
      an[IllegalArgumentException] should be thrownBy RemoteA2aAgent.named("")
    }

    "reject a negative timeout" in {
      an[IllegalArgumentException] should be thrownBy RemoteA2aAgent.named("claims").withTimeout(Duration.ofSeconds(-1))
    }
  }
}
