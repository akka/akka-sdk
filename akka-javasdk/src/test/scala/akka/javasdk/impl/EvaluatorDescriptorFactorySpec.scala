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

  // load the config over reference.conf, the same way an application.conf is loaded
  private def load(config: String): Config =
    ConfigFactory.load(ConfigFactory.parseString(config))

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
      val bindings = EvaluatorSettings.agentBindings(config, "conversation-quality")
      bindings should have size 2
      agentBindingIds(bindings) should contain theSameElementsAs Seq("support-agent", "billing-agent")
      bindings.head.asInstanceOf[SpiEvaluator.AgentBinding].event shouldBe SpiEvaluator.AgentBindingEvent.Interaction
    }

    "read a single bound agent" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = interaction }
        """)
      agentBindingIds(
        EvaluatorSettings.agentBindings(config, "conversation-quality")) should contain only "support-agent"
    }

    "produce no bindings when the evaluator is not configured" in {
      EvaluatorSettings.agentBindings(load(""), "conversation-quality") shouldBe empty
    }

    "produce no bindings when the evaluator has no agents configured" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality {}
        """)
      EvaluatorSettings.agentBindings(config, "conversation-quality") shouldBe empty
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
      EvaluatorSettings.agentBindings(config, "conversation-quality") shouldBe empty
    }

    "require a trigger on an enabled binding" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent {}
        """)
      val ex = intercept[IllegalArgumentException] {
        EvaluatorSettings.agentBindings(config, "conversation-quality")
      }
      ex.getMessage should include("trigger")
    }

    "reject an unknown binding trigger" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents.support-agent { trigger = nonsense }
        """)
      val ex = intercept[IllegalArgumentException] {
        EvaluatorSettings.agentBindings(config, "conversation-quality")
      }
      ex.getMessage should include("nonsense")
    }

    "exclude agents whose binding is disabled" in {
      val config = load("""
        akka.javasdk.evaluation.evaluators.conversation-quality.agents {
          support-agent { trigger = interaction }
          billing-agent { enabled = false }
        }
        """)
      agentBindingIds(
        EvaluatorSettings.agentBindings(config, "conversation-quality")) should contain only "support-agent"
    }
  }
}
