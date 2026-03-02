/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeWorkflowValidationSpec extends AbstractWorkflowValidationSpec(CompileTimeValidation)
class RuntimeWorkflowValidationSpec extends AbstractWorkflowValidationSpec(RuntimeValidation)

abstract class AbstractWorkflowValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"Workflow validation ($validationMode)" should {

    "accept valid Workflow with 0-arg command handler" in {
      assertValid("valid/ValidWorkflowNoArg.java")
    }

    "accept valid Workflow with 1-arg command handler" in {
      assertValid("valid/ValidWorkflowOneArg.java")
    }

    "reject Workflow with command handler with 2 arguments" in {
      assertInvalid("invalid/WorkflowTwoArgs.java", "Method [execute] must have zero or one argument")
    }

    "accept valid Workflow with 0-arg StepMethod" in {
      assertValid("valid/ValidWorkflowStepNoArg.java")
    }

    "accept valid Workflow with 1-arg StepMethod" in {
      assertValid("valid/ValidWorkflowStepOneArg.java")
    }

    "reject Workflow with StepEffect method with 2 arguments" in {
      assertInvalid("invalid/WorkflowStepTwoArgs.java", "WorkflowStepTwoArgs", "zero or one argument")
    }

    "reject Workflow with ReadOnlyEffect method with 2 arguments" in {
      assertInvalid("invalid/WorkflowReadOnlyTwoArgs.java", "WorkflowReadOnlyTwoArgs", "zero or one argument")
    }

    "reject Workflow with no Effect method" in {
      assertInvalid("invalid/WorkflowNoEffect.java", "No public method returning akka.javasdk.workflow.Workflow.Effect")
    }

    "accept Workflow with inherited Effect method" in {
      assertValid("valid/WorkflowInheritedEffect.java")
    }

    "reject Workflow that is not public" in {
      assertInvalid(
        "invalid/NotPublicWorkflow.java",
        "NotPublicWorkflow is not marked with `public` modifier. Components must be public.")
    }

    "accept Workflow with @FunctionTool on Effect and ReadOnlyEffect" in {
      assertValid("valid/ValidWorkflowWithFunctionTool.java")
    }

    "reject Workflow with @FunctionTool on StepEffect" in {
      assertInvalid(
        "invalid/WorkflowWithFunctionToolOnStepEffect.java",
        "Workflow methods annotated with @FunctionTool cannot return StepEffect.",
        "Only methods returning Effect or ReadOnlyEffect can be annotated with @FunctionTool.")
    }

    "reject Workflow with @FunctionTool on private StepEffect" in {
      // This test needs special handling for the "not contain" assertion
      assertInvalid(
        "invalid/WorkflowWithFunctionToolOnPrivateStepEffect.java",
        "Workflow methods annotated with @FunctionTool cannot return StepEffect.",
        "Only methods returning Effect or ReadOnlyEffect can be annotated with @FunctionTool.")
    }

    "reject Workflow with @FunctionTool on private methods" in {
      assertInvalid(
        "invalid/WorkflowWithFunctionToolOnPrivateMethod.java",
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }
  }
}
