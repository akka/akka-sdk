/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.workflow

import akka.javasdk.testmodels.workflow.WorkflowTestModels
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowDescriptorSpec extends AnyWordSpec with OptionValues with Matchers {

  "Workflow descriptor" should {
    "find step by method name" in {
      val workflow = new WorkflowTestModels.TransferWorkflow()
      val descriptor = new WorkflowDescriptor(workflow)

      descriptor.findStepMethodByName("depositStep") shouldBe defined
    }

    "find steps by name defined in annotation" in {
      val workflow = new WorkflowTestModels.TransferWorkflow()
      val descriptor = new WorkflowDescriptor(workflow)

      descriptor.findStepMethodByName("withdraw") shouldBe defined
    }
  }

  "Workflow step names" should {
    "use the annotated name or the method name, sorted" in {
      WorkflowDescriptor.stepNames(classOf[WorkflowTestModels.TransferWorkflow]) shouldBe Seq("depositStep", "withdraw")
    }

    "include a step of a super class" in {
      WorkflowDescriptor.stepNames(classOf[WorkflowTestModels.WorkflowHierarchy]) shouldBe Seq("processStep")
    }

    "list an overridden step once and leave out generated methods and methods with two inputs" in {
      WorkflowDescriptor.stepNames(classOf[WorkflowTestModels.OverridingStepsWorkflow]) shouldBe Seq(
        "named",
        "sharedStep")
    }

    "list only names that dispatch finds" in {
      val workflows = Seq(
        new WorkflowTestModels.TransferWorkflow(),
        new WorkflowTestModels.WorkflowHierarchy(),
        new WorkflowTestModels.OverridingStepsWorkflow())

      workflows.foreach { workflow =>
        val descriptor = new WorkflowDescriptor(workflow)
        val names = WorkflowDescriptor.stepNames(workflow.getClass)
        names should not be empty
        names.foreach { name =>
          withClue(s"${workflow.getClass.getSimpleName} step [$name]: ") {
            descriptor.findStepMethodByName(name) shouldBe defined
          }
        }
      }
    }

    "be empty for a workflow class with no step" in {
      WorkflowDescriptor.stepNames(classOf[WorkflowTestModels.Swarm[_, _]]) shouldBe empty
    }
  }
}
