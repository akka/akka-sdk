/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.testmodels.workflow.WorkflowTestModels.InvalidWorkflowWithTwoArgCommandHandler
import akka.javasdk.testmodels.workflow.WorkflowTestModels.ValidWorkflowWithNoArgCommandHandler
import akka.javasdk.testmodels.workflow.WorkflowTestModels.ValidWorkflowWithOneArgCommandHandler
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithFunctionToolOnNonEffectMethod
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithFunctionToolOnReadOnlyEffect
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithFunctionToolOnStepEffect
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithNonEffectMethod
import akka.javasdk.testmodels.workflow.WorkflowTestModels.WorkflowWithValidFunctionTool
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "Workflow validation" should {

    "return Valid for Workflow with command handler with 0 arguments" in {
      val result = Validations.validate(classOf[ValidWorkflowWithNoArgCommandHandler])
      result.isValid shouldBe true
    }

    "return Valid for Workflow with command handler with 1 argument" in {
      val result = Validations.validate(classOf[ValidWorkflowWithOneArgCommandHandler])
      result.isValid shouldBe true
    }

    "return Invalid for Workflow with command handler with 2 arguments" in {
      Validations
        .validate(classOf[InvalidWorkflowWithTwoArgCommandHandler])
        .expectInvalid("Method [execute] must have zero or one argument")
    }

    "return Inalid for Workflow with a method not returning an Effect" in {
      Validations
        .validate(classOf[WorkflowWithNonEffectMethod])
        .expectInvalid("No method returning akka.javasdk.workflow.Workflow$Effect")
    }

    "validate a Workflow must be declared as public" in {
      Validations
        .validate(classOf[NotPublicComponents.NotPublicWorkflow])
        .expectInvalid("NotPublicWorkflow is not marked with `public` modifier. Components must be public.")
    }

    "return Invalid for Workflow with @FunctionTool on StepEffect method" in {
      Validations
        .validate(classOf[WorkflowWithFunctionToolOnStepEffect])
        .expectInvalid("@FunctionTool cannot be used on step methods (methods returning Workflow.StepEffect)")
    }

    "return Invalid for Workflow with @FunctionTool on non-Effect method" in {
      Validations
        .validate(classOf[WorkflowWithFunctionToolOnNonEffectMethod])
        .expectInvalid(
          "@FunctionTool can only be used on command handler methods returning Workflow.Effect or Workflow.ReadOnlyEffect")
    }

    "allow @FunctionTool on valid Effect method" in {
      Validations.validate(classOf[WorkflowWithValidFunctionTool]).isValid shouldBe true
    }

    "allow @FunctionTool on ReadOnlyEffect method" in {
      Validations.validate(classOf[WorkflowWithFunctionToolOnReadOnlyEffect]).isValid shouldBe true
    }
  }
}
