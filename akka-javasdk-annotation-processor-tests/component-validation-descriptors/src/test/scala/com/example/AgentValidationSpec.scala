/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class AgentValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "Agent validation" should {

    // Valid agents
    "accept valid Agent with one Effect method" in {
      val result = compileTestSource("valid/ValidAgent.java")
      assertCompilationSuccess(result)
    }

    "accept valid Agent with @AgentDescription" in {
      val result = compileTestSource("valid/ValidAgentWithAgentDescription.java")
      assertCompilationSuccess(result)
    }

    "accept valid Agent with StreamEffect" in {
      val result = compileTestSource("valid/ValidAgentWithStreamEffect.java")
      assertCompilationSuccess(result)
    }

    "accept valid Agent command handler with zero arguments" in {
      val result = compileTestSource("valid/ValidAgentWithZeroArgs.java")
      assertCompilationSuccess(result)
    }

    // Command handler validations
    "reject Agent without command handler" in {
      val result = compileTestSource("invalid/AgentWithNoCommandHandler.java")
      assertCompilationFailure(
        result,
        "has 0 command handlers",
        "There must be one public method returning Agent.Effect")
    }

    "reject Agent with multiple command handlers" in {
      val result = compileTestSource("invalid/AgentWithMultipleCommandHandlers.java")
      assertCompilationFailure(
        result,
        "has 2 command handlers",
        "There must be one public method returning Agent.Effect")
    }

    "reject Agent with command handler having too many parameters" in {
      val result = compileTestSource("invalid/AgentWithTooManyParams.java")
      assertCompilationFailure(result, "Method [query] must have zero or one argument", "wrap them in a class")
    }

    // @AgentDescription validation
    "reject Agent with both @AgentDescription and @Component name/description" in {
      val result = compileTestSource("invalid/AgentWithBothDescriptionAnnotations.java")
      assertCompilationFailure(
        result,
        "@AgentDescription.name and @Component.name are defined",
        "@AgentDescription.description and @Component.description are defined")
    }

    "reject Agent with both @AgentDescription.role and @AgentRole" in {
      val result = compileTestSource("invalid/AgentWithBothRoleAnnotations.java")
      assertCompilationFailure(result, "@AgentDescription.role and @AgentRole are defined", "use only @AgentRole")
    }

    "reject Agent with empty @AgentDescription.name (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithEmptyAgentDescriptionName.java")
      assertCompilationFailure(result, "@AgentDescription.name is empty")
    }

    "reject Agent with empty @AgentDescription.description (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithEmptyAgentDescriptionDescription.java")
      assertCompilationFailure(result, "@AgentDescription.description is empty")
    }

    "reject Agent with blank @AgentDescription.name (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithBlankAgentDescriptionName.java")
      assertCompilationFailure(result, "@AgentDescription.name is empty")
    }

    "reject Agent with blank @AgentDescription.description (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithBlankAgentDescriptionDescription.java")
      assertCompilationFailure(result, "@AgentDescription.description is empty")
    }
  }
}
