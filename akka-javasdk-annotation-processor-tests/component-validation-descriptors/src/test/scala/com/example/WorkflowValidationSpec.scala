/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class WorkflowValidationSpec extends AnyWordSpec with Matchers with CompilationTestSupport {

  "Workflow validation" should {

    "accept valid Workflow with 0-arg command handler" in {
      val result = compileTestSource("valid/ValidWorkflowNoArg.java")
      assertCompilationSuccess(result)
    }

    "accept valid Workflow with 1-arg command handler" in {
      val result = compileTestSource("valid/ValidWorkflowOneArg.java")
      assertCompilationSuccess(result)
    }

    "reject Workflow with command handler with 2 arguments" in {
      val result = compileTestSource("invalid/WorkflowTwoArgs.java")
      assertCompilationFailure(result, "Method [execute] must have zero or one argument")
    }

    "accept valid Workflow with 0-arg StepMethod" in {
      val result = compileTestSource("valid/ValidWorkflowStepNoArg.java")
      assertCompilationSuccess(result)
    }

    "accept valid Workflow with 1-arg StepMethod" in {
      val result = compileTestSource("valid/ValidWorkflowStepOneArg.java")
      assertCompilationSuccess(result)
    }

    "reject Workflow with StepEffect method with 2 arguments" in {
      val result = compileTestSource("invalid/WorkflowStepTwoArgs.java")
      assertCompilationFailure(result, "WorkflowStepTwoArgs", "zero or one argument")
    }

    "reject Workflow with ReadOnlyEffect method with 2 arguments" in {
      val result = compileTestSource("invalid/WorkflowReadOnlyTwoArgs.java")
      assertCompilationFailure(result, "WorkflowReadOnlyTwoArgs", "zero or one argument")
    }

    "reject Workflow with no Effect method" in {
      val result = compileTestSource("invalid/WorkflowNoEffect.java")
      assertCompilationFailure(result, "No method returning akka.javasdk.workflow.Workflow.Effect")
    }

    "reject Workflow that is not public" in {
      val result = compileTestSource("invalid/NotPublicWorkflow.java")
      assertCompilationFailure(
        result,
        "NotPublicWorkflow is not marked with `public` modifier. Components must be public.")
    }

    "accept Workflow with @FunctionTool on Effect and ReadOnlyEffect" in {
      val result = compileTestSource("valid/ValidWorkflowWithFunctionTool.java")
      assertCompilationSuccess(result)
    }

    "reject Workflow with @FunctionTool on StepEffect" in {
      val result = compileTestSource("invalid/WorkflowWithFunctionToolOnStepEffect.java")
      assertCompilationFailure(
        result,
        "Workflow methods annotated with @FunctionTool cannot return StepEffect.",
        "Only methods returning Effect or ReadOnlyEffect can be annotated with @FunctionTool.")
    }

    "reject Workflow with @FunctionTool on private StepEffect" in {
      val result = compileTestSource("invalid/WorkflowWithFunctionToolOnPrivateStepEffect.java")
      assertCompilationFailure(
        result,
        "Workflow methods annotated with @FunctionTool cannot return StepEffect.",
        "Only methods returning Effect or ReadOnlyEffect can be annotated with @FunctionTool.")

      assertCompilationFailureNotContain(result, "Methods annotated with @FunctionTool must be public.")
    }

    "reject Workflow with @FunctionTool on private methods" in {
      val result = compileTestSource("invalid/WorkflowWithFunctionToolOnPrivateMethod.java")
      assertCompilationFailure(
        result,
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }
  }
}
