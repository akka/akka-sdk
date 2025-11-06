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

    "accept valid Agent with @FunctionTool on non-command handler methods" in {
      val result = compileTestSource("valid/ValidAgentWithFunctionTool.java")
      assertCompilationSuccess(result)
    }

    // Command handler validations
    "reject Agent without Effect method" in {
      val result = compileTestSource("invalid/AgentWithoutEffectMethod.java")
      assertCompilationFailure(
        result,
        "No method returning akka.javasdk.agent.Agent.Effect, akka.javasdk.agent.Agent.StreamEffect found")
    }

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
      assertCompilationFailure(
        result,
        "Method [query] must have zero or one argument",
        "If you need to pass more arguments, wrap them in a class")
    }

    "reject Agent with @FunctionTool on command handler" in {
      val result = compileTestSource("invalid/AgentWithFunctionToolOnCommandHandler.java")
      assertCompilationFailure(result, "Agent command handler methods cannot be annotated with @FunctionTool.")
    }

    "reject Agent with both @AgentDescription and @Component name/description" in {
      val result = compileTestSource("invalid/AgentWithBothDescriptionAnnotations.java")
      assertCompilationFailure(
        result,
        "Both @AgentDescription.description and @Component.description are defined.",
        "Remove @AgentDescription.description and use only @Component.description",
        "Both @AgentDescription.name and @Component.name are defined.",
        "Remove @AgentDescription.name and use only @Component.name.")
    }

    "reject Agent with both @AgentDescription.role and @AgentRole" in {
      val result = compileTestSource("invalid/AgentWithBothRoleAnnotations.java")
      assertCompilationFailure(
        result,
        "Both @AgentDescription.role and @AgentRole are defined.",
        "Remove @AgentDescription.role and use only @AgentRole.")
    }

    "reject Agent with empty @AgentDescription.name (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithEmptyAgentDescriptionName.java")
      assertCompilationFailure(
        result,
        "@AgentDescription.name is empty",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with empty @AgentDescription.description (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithEmptyAgentDescriptionDescription.java")
      assertCompilationFailure(
        result,
        "@AgentDescription.description is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with blank @AgentDescription.name (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithBlankAgentDescriptionName.java")
      assertCompilationFailure(
        result,
        "@AgentDescription.name is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with blank @AgentDescription.description (no @Component)" in {
      val result = compileTestSource("invalid/AgentWithBlankAgentDescriptionDescription.java")
      assertCompilationFailure(
        result,
        "@AgentDescription.description is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }
  }
}
