/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.reflection.Reflect
import akka.javasdk.impl.serialization.Serializer
import akka.javasdk.impl.workflow.WorkflowDescriptor
import akka.javasdk.testmodels.inheritance.InheritedHandlerModels
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class InheritedCommandHandlerSpec extends AnyWordSpec with OptionValues with Matchers {

  private val serializer = new Serializer()

  private def invokerNames(component: Class[_]): Set[String] =
    ComponentDescriptor.descriptorFor(component, serializer).methodInvokers.keySet

  "Command handler discovery" should {

    "pick up Event Sourced Entity command handlers inherited from a base class" in {
      invokerNames(classOf[InheritedHandlerModels.InheritingEsEntity]) should ===(
        Set("InheritedIncrease", "InheritedGet", "OwnCommand"))
    }

    "pick up Key Value Entity command handlers inherited from a base class" in {
      invokerNames(classOf[InheritedHandlerModels.InheritingKvEntity]) should ===(
        Set("InheritedSet", "InheritedGet", "OwnSet"))
    }

    "pick up Timed Action command handlers inherited from a base class" in {
      invokerNames(classOf[InheritedHandlerModels.InheritingTimedAction]) should ===(
        Set("InheritedCommand", "OwnCommand"))
    }

    "pick up the Agent command handler inherited from a base class" in {
      invokerNames(classOf[InheritedHandlerModels.InheritingAgent]) should ===(Set("InheritedQuery"))
    }

    "pick up Workflow command handlers inherited from a base class" in {
      invokerNames(classOf[InheritedHandlerModels.InheritingWorkflow]) should ===(
        Set("InheritedStart", "InheritedGet", "OwnCommand"))
    }

    "register input types for Workflow steps inherited from a base class" in {
      Reflect.workflowKnownInputTypes(classOf[InheritedHandlerModels.InheritingWorkflow]) should contain(
        classOf[InheritedHandlerModels.StepInput])
    }

    "find a Workflow step method inherited from a base class" in {
      val descriptor = new WorkflowDescriptor(new InheritedHandlerModels.InheritingWorkflow())
      descriptor.findStepMethodByName("inheritedStep") shouldBe defined
    }
  }
}
