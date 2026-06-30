/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.MultiBindingEvaluator
import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.NoBindingEvaluator
import akka.javasdk.testmodels.evaluation.EvaluatorTestModels.SingleBindingEvaluator
import akka.javasdk.tooling.validation.Validation
import akka.javasdk.tooling.validation.Validations
import akka.javasdk.validation.ast.runtime.RuntimeTypeDef
import akka.runtime.sdk.spi.SpiEvaluator
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EvaluatorDescriptorFactorySpec extends AnyWordSpec with Matchers {

  private def agentBindingIds(bindings: Seq[SpiEvaluator.Binding]): Seq[String] =
    bindings.collect { case ab: SpiEvaluator.AgentBinding => ab.agentComponentId }

  "Evaluator descriptor factory" should {

    "be selected for evaluator components" in {
      Reflect.isEvaluator(classOf[SingleBindingEvaluator]) shouldBe true
      ComponentDescriptorFactory.getFactoryFor(classOf[SingleBindingEvaluator]) shouldBe
      EvaluatorDescriptorFactory
    }

    "produce an empty component descriptor (single abstract handler, no command routing)" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SingleBindingEvaluator], new Serializer)
      desc.methodInvokers shouldBe empty
    }

    "read a single @EvaluatesAgent binding" in {
      val bindings = EvaluatorDescriptorFactory.agentBindings(classOf[SingleBindingEvaluator])
      bindings should have size 1
      agentBindingIds(bindings) should contain only "support-agent"
      bindings.head.asInstanceOf[SpiEvaluator.AgentBinding].event shouldBe
      SpiEvaluator.AgentBindingEvent.Interaction
    }

    "read repeated @EvaluatesAgent bindings" in {
      val bindings = EvaluatorDescriptorFactory.agentBindings(classOf[MultiBindingEvaluator])
      bindings should have size 2
      agentBindingIds(bindings) should contain theSameElementsAs Seq("support-agent", "billing-agent")
    }

    "produce no bindings when @EvaluatesAgent is absent" in {
      EvaluatorDescriptorFactory.agentBindings(classOf[NoBindingEvaluator]) shouldBe empty
    }
  }

  "Evaluator validation" should {

    "accept an evaluator with one or more @EvaluatesAgent bindings" in {
      Validations.validate(new RuntimeTypeDef(classOf[SingleBindingEvaluator])) shouldBe a[Validation.Valid]
      Validations.validate(new RuntimeTypeDef(classOf[MultiBindingEvaluator])) shouldBe a[Validation.Valid]
    }

    "reject an evaluator with no @EvaluatesAgent binding" in {
      Validations.validate(new RuntimeTypeDef(classOf[NoBindingEvaluator])) match {
        case invalid: Validation.Invalid =>
          invalid.messages.toString should include("must evaluate at least one agent")
        case other =>
          fail(s"expected invalid validation, got [$other]")
      }
    }
  }
}
