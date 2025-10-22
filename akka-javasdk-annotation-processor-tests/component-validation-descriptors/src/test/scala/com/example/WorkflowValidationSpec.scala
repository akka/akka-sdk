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
      assertCompilationFailure(result, "WorkflowTwoArgs", "zero or one argument")
    }

    "reject Workflow with no Effect method" in {
      val result = compileTestSource("invalid/WorkflowNoEffect.java")
      assertCompilationFailure(result, "WorkflowNoEffect", "No method returning", "Effect")
    }

    "reject Workflow that is not public" in {
      val result = compileTestSource("invalid/NotPublicWorkflow.java")
      assertCompilationFailure(result, "NotPublicWorkflow", "not marked with `public` modifier")
    }

    "reject Workflow with StepEffect method with 2 arguments" in {
      val result = compileTestSource("invalid/WorkflowStepTwoArgs.java")
      assertCompilationFailure(result, "WorkflowStepTwoArgs", "zero or one argument")
    }
  }
}
