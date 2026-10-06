/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.evaluation.EvaluatorSettings
import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.SomeEvaluator
import akka.runtime.sdk.spi.SpiEvaluator
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EvaluatorDescriptorFactorySpec extends AnyWordSpec with Matchers {

  private def agentBindingIds(bindings: Seq[SpiEvaluator.Binding]): Seq[String] =
    bindings.collect { case ab: SpiEvaluator.AgentBinding => ab.agentComponentId }

  // the agent component id, the sampling ratio and the failure flag of each agent binding
  private def agentBindingSettings(bindings: Seq[SpiEvaluator.Binding]): Seq[(String, Double, Boolean)] =
    bindings.collect { case ab: SpiEvaluator.AgentBinding =>
      (ab.agentComponentId, ab.samplingRatio, ab.triggerOnFailure)
    }

  // load the config over reference.conf, the same way an application.conf is loaded
  private def load(config: String): Config =
    ConfigFactory.load(ConfigFactory.parseString(config))

  private val NoAgentRoles = Map.empty[String, Option[String]]

  // the agents of the service, by component id, with their role
  private val agentRoles = Map(
    "support-agent" -> Some("customer-facing"),
    "billing-agent" -> Some("customer-facing"),
    "audit-agent" -> Some("internal"),
    "plain-agent" -> None)

  "Evaluator descriptor factory" should {

    "be selected for evaluator components" in {
      Reflect.isEvaluator(classOf[SomeEvaluator]) shouldBe true
      ComponentDescriptorFactory.getFactoryFor(classOf[SomeEvaluator]) shouldBe EvaluatorDescriptorFactory
    }

    "produce an empty component descriptor (single abstract handler, no command routing)" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SomeEvaluator], new Serializer)
      desc.methodInvokers shouldBe empty
    }
  }

  "Evaluator config bindings" should {

    "read the agents bound to an evaluator from config" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators {
          conversation-quality {
            agents {
              support-agent { trigger = interaction }
              billing-agent { trigger = interaction }
            }
          }
        }
        """)
      val bindings = EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)
      bindings should have size 2
      agentBindingIds(bindings) should contain theSameElementsAs Seq("support-agent", "billing-agent")
      bindings.head.asInstanceOf[SpiEvaluator.AgentBinding].event shouldBe SpiEvaluator.AgentBindingEvent.Interaction
    }

    "read a single bound agent" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = interaction }
        """)
      agentBindingIds(
        EvaluatorSettings
          .agentBindings(config, "conversation-quality", NoAgentRoles)) should contain only "support-agent"
    }

    "produce no bindings when the evaluator is not configured" in {
      EvaluatorSettings.agentBindings(load(""), "conversation-quality", NoAgentRoles) shouldBe empty
    }

    "produce no bindings when the evaluator has no agents configured" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {}
        """)
      EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles) shouldBe empty
    }

    "produce no bindings when the evaluator is disabled" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          enabled = false
          agents {
            support-agent {}
          }
        }
        """)
      EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles) shouldBe empty
    }

    "require a trigger on an enabled binding" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent {}
        """)
      val ex = intercept[IllegalArgumentException] {
        EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)
      }
      ex.getMessage should include("trigger")
    }

    "reject an unknown binding trigger" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = nonsense }
        """)
      val ex = intercept[IllegalArgumentException] {
        EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)
      }
      ex.getMessage should include("nonsense")
    }

    "bind the agents that have a role" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agent-roles {
          customer-facing { trigger = interaction }
        }
        """)
      val bindings = EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)
      agentBindingIds(bindings) shouldBe Seq("billing-agent", "support-agent")
      bindings.head.asInstanceOf[SpiEvaluator.AgentBinding].event shouldBe SpiEvaluator.AgentBindingEvent.Interaction
    }

    "bind every agent that has a role for the role wildcard" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agent-roles {
          "*" { trigger = interaction }
        }
        """)
      agentBindingIds(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq("audit-agent", "billing-agent", "support-agent")
    }

    "let the entry for the agent decide over the entry for its role" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents {
            billing-agent { enabled = false }
            plain-agent { trigger = interaction }
          }
          agent-roles {
            customer-facing { trigger = interaction }
          }
        }
        """)
      agentBindingIds(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq("plain-agent", "support-agent")
    }

    "let the entry for a role decide over the role wildcard" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agent-roles {
          "*" { trigger = interaction }
          internal { enabled = false }
        }
        """)
      agentBindingIds(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq("billing-agent", "support-agent")
    }

    "require a trigger on an enabled role binding" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agent-roles.customer-facing {}
        """)
      val ex = intercept[IllegalArgumentException] {
        EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)
      }
      ex.getMessage should include("Evaluator agent role binding [customer-facing] must specify 'trigger'")
    }

    "exclude agents whose binding is disabled" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents {
          support-agent { trigger = interaction }
          billing-agent { enabled = false }
        }
        """)
      agentBindingIds(
        EvaluatorSettings
          .agentBindings(config, "conversation-quality", NoAgentRoles)) should contain only "support-agent"
    }

    "evaluate every successful interaction when a binding sets no sampling ratio and no failure flag" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents.plain-agent { trigger = interaction }
          agent-roles.customer-facing { trigger = interaction }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq(("billing-agent", 1.0, false), ("plain-agent", 1.0, false), ("support-agent", 1.0, false))
    }

    "hand the sampling ratio and the failure flag of an entry under agents to the runtime" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents {
          support-agent { trigger = interaction, sampling-ratio = 0.1, trigger-on-failure = true }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)) shouldBe
      Seq(("support-agent", 0.1, true))
    }

    "hand the sampling ratio and the failure flag of an entry under agent-roles to the runtime, for each agent" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agent-roles {
          customer-facing { trigger = interaction, sampling-ratio = 0.1, trigger-on-failure = true }
          "*" { trigger = interaction, sampling-ratio = 0.2, trigger-on-failure = true }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq(("audit-agent", 0.2, true), ("billing-agent", 0.1, true), ("support-agent", 0.1, true))
    }

    "take the sampling ratio and the failure flag from the entry for the agent, not from the entry for its role" in {
      val roleSetsBoth = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents.support-agent { trigger = interaction }
          agent-roles.customer-facing { trigger = interaction, sampling-ratio = 0.1, trigger-on-failure = true }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(roleSetsBoth, "conversation-quality", agentRoles)) shouldBe
      Seq(("billing-agent", 0.1, true), ("support-agent", 1.0, false))

      val agentSetsBoth = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents.support-agent { trigger = interaction, sampling-ratio = 0.1, trigger-on-failure = true }
          agent-roles.customer-facing { trigger = interaction }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(agentSetsBoth, "conversation-quality", agentRoles)) shouldBe
      Seq(("billing-agent", 1.0, false), ("support-agent", 0.1, true))
    }

    "take the sampling ratio and the failure flag from the agent defaults when a binding does not set them" in {
      val config = load("""
        akka.javasdk.evaluation.defaults.agent {
          sampling-ratio = 0.25
          trigger-on-failure = true
        }
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents {
            plain-agent { trigger = interaction }
            support-agent { trigger = interaction, sampling-ratio = 0.5, trigger-on-failure = false }
          }
          agent-roles {
            customer-facing { trigger = interaction }
            internal { trigger = interaction, sampling-ratio = 0.75, trigger-on-failure = false }
          }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe
      Seq(
        ("audit-agent", 0.75, false),
        ("billing-agent", 0.25, true),
        ("plain-agent", 0.25, true),
        ("support-agent", 0.5, false))
    }

    Seq(
      "agent binding [support-agent]" -> "agents.support-agent",
      "agent role binding [customer-facing]" -> "agent-roles.customer-facing").foreach { case (binding, path) =>
      s"accept a sampling ratio of 0.0 and 1.0 on the $binding" in {
        Seq(0.0, 1.0).foreach { ratio =>
          val config = load(s"""
            akka.javasdk.evaluation.evaluators.conversation-quality.$path {
              trigger = interaction
              sampling-ratio = $ratio
            }
            """)
          agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles))
            .find(_._1 == "support-agent") shouldBe Some(("support-agent", ratio, false))
        }
      }

      Seq("-0.1" -> "-0.1", "1.1" -> "1.1", "10" -> "10.0", "NaN" -> "NaN").foreach { case (ratio, shown) =>
        s"reject sampling-ratio = $ratio on the $binding" in {
          val config = load(s"""
            akka.javasdk.evaluation.evaluators.conversation-quality.$path {
              trigger = interaction
              sampling-ratio = $ratio
            }
            """)
          intercept[IllegalArgumentException] {
            EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)
          }.getMessage shouldBe
          s"Evaluator $binding must define [sampling-ratio] between 0.0 and 1.0, but defines [$shown]"
        }
      }
    }

    "not validate the sampling ratio of a disabled binding" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          agents.support-agent { enabled = false, sampling-ratio = 10 }
          agent-roles.internal { enabled = false, sampling-ratio = 10 }
        }
        """)
      agentBindingSettings(EvaluatorSettings.agentBindings(config, "conversation-quality", agentRoles)) shouldBe empty
    }

    Seq("sampling-ratio" -> "0.1", "trigger-on-failure" -> "true").foreach { case (key, value) =>
      val expectedMessage =
        s"Evaluator [conversation-quality] must define [$key] in a binding or in " +
        "[akka.javasdk.evaluation.defaults.agent], not on the evaluator or in " +
        "[akka.javasdk.evaluation.defaults.evaluator]"

      s"reject $key on the evaluator, also when the evaluator is disabled" in {
        Seq(true, false).foreach { enabled =>
          val config = load(s"""
            akka.javasdk.evaluation.evaluators.conversation-quality {
              enabled = $enabled
              $key = $value
              agents.support-agent { trigger = interaction }
            }
            """)
          intercept[IllegalArgumentException] {
            EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)
          }.getMessage shouldBe expectedMessage
        }
      }

      s"reject $key in akka.javasdk.evaluation.defaults.evaluator, also when the evaluator is disabled" in {
        Seq(true, false).foreach { enabled =>
          val config = load(s"""
            akka.javasdk.evaluation.defaults.evaluator.$key = $value
            akka.javasdk.evaluation.evaluators.conversation-quality {
              enabled = $enabled
              agents.support-agent { trigger = interaction }
            }
            """)
          intercept[IllegalArgumentException] {
            EvaluatorSettings.agentBindings(config, "conversation-quality", NoAgentRoles)
          }.getMessage shouldBe expectedMessage
        }
      }
    }
  }

  "Evaluator control id" should {

    "be read from the entry of the evaluator" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          control-id = "AI-EV-01"
          agents.support-agent { trigger = interaction }
        }
        """)
      EvaluatorSettings.controlId(config, "conversation-quality") shouldBe Some("AI-EV-01")
    }

    "be read from a disabled evaluator" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          enabled = false
          control-id = "AI-EV-01"
        }
        """)
      EvaluatorSettings.controlId(config, "conversation-quality") shouldBe Some("AI-EV-01")
    }

    "be None when the evaluator has no entry, or its entry has no control id" in {
      EvaluatorSettings.controlId(load(""), "conversation-quality") shouldBe None
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = interaction }
        """)
      EvaluatorSettings.controlId(config, "conversation-quality") shouldBe None
    }

    Seq("\"\"", "\" \"").foreach { blank =>
      s"reject control-id = $blank" in {
        val config = load(s"""
          akka.javasdk.evaluation.evaluators.conversation-quality.control-id = $blank
          """)
        intercept[IllegalArgumentException] {
          EvaluatorSettings.controlId(config, "conversation-quality")
        }.getMessage shouldBe "Evaluator [conversation-quality] must define a non blank [control-id]"
      }
    }

    "reject a control id that is not a string" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.control-id = true
        """)
      intercept[IllegalArgumentException] {
        EvaluatorSettings.controlId(config, "conversation-quality")
      }.getMessage shouldBe
      "Evaluator [conversation-quality] must define [control-id] as a string, but defines [BOOLEAN]"
    }

    Seq(
      "agents.support-agent" -> """agents.support-agent { trigger = interaction, control-id = "AI-EV-01" }""",
      "agent-roles.*" -> """agent-roles."*" { trigger = interaction, control-id = "AI-EV-01" }""").foreach {
      case (path, binding) =>
        s"reject a control id in the binding $path, also when the evaluator is disabled" in {
          Seq(true, false).foreach { enabled =>
            val config = load(s"""
              akka.javasdk.evaluation.evaluators.conversation-quality {
                enabled = $enabled
                $binding
              }
              """)
            intercept[IllegalArgumentException] {
              EvaluatorSettings.controlId(config, "conversation-quality")
            }.getMessage shouldBe
            s"Evaluator [conversation-quality] must define [control-id] on the evaluator, not in [$path]"
          }
        }
    }

    Seq("akka.javasdk.evaluation.defaults.evaluator", "akka.javasdk.evaluation.defaults.agent").foreach { path =>
      s"reject a control id in $path" in {
        val config = load(s"""
          $path.control-id = "AI-EV-01"
          akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = interaction }
          """)
        intercept[IllegalArgumentException] {
          EvaluatorSettings.controlId(config, "conversation-quality")
        }.getMessage shouldBe
        s"Evaluator [conversation-quality] must define [control-id] on the evaluator, not in [$path]"
      }
    }

    "be handed to the runtime with the bindings, on the descriptor of an evaluator and of a durable evaluator" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {
          control-id = "AI-EV-01"
          agents.support-agent { trigger = interaction, sampling-ratio = 0.1, trigger-on-failure = true }
        }
        """)
      val configured = EvaluatorSettings.configuredEvaluator(config, "conversation-quality", NoAgentRoles)

      val evaluator = configured.evaluatorDescriptor(
        "conversation-quality",
        classOf[SomeEvaluator].getName,
        name = None,
        description = None,
        instanceFactory = _ => throw new UnsupportedOperationException,
        provided = false)
      agentBindingSettings(evaluator.bindings) shouldBe Seq(("support-agent", 0.1, true))
      evaluator.controlId shouldBe Some("AI-EV-01")

      val durable = configured.workflowEvaluatorDescriptor(
        "conversation-quality",
        classOf[SomeEvaluator].getName,
        name = None,
        description = None,
        instanceFactory = _ => throw new UnsupportedOperationException,
        provided = false)
      agentBindingSettings(durable.bindings) shouldBe Seq(("support-agent", 0.1, true))
      durable.controlId shouldBe Some("AI-EV-01")
    }
  }
}
