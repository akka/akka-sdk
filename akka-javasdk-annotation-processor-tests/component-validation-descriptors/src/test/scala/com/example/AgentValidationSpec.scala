/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeAgentValidationSpec extends AbstractAgentValidationSpec(CompileTimeValidation)
class RuntimeAgentValidationSpec extends AbstractAgentValidationSpec(RuntimeValidation)

abstract class AbstractAgentValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"Agent validation ($validationMode)" should {

    // Valid agents
    "accept valid Agent with one Effect method" in {
      assertValid("valid/ValidAgent.java")
    }

    "accept valid Agent with @AgentDescription" in {
      assertValid("valid/ValidAgentWithAgentDescription.java")
    }

    "accept valid Agent with StreamEffect" in {
      assertValid("valid/ValidAgentWithStreamEffect.java")
    }

    "accept valid Agent command handler with zero arguments" in {
      assertValid("valid/ValidAgentWithZeroArgs.java")
    }

    "accept valid Agent with @FunctionTool on non-command handler methods" in {
      assertValid("valid/ValidAgentWithFunctionTool.java")
    }

    "accept Agent with multiple private command handlers" in {
      assertValid("valid/AgentWithMultipleCommandPrivateHandlers.java")
    }

    "accept Agent with @FunctionTool on private methods" in {
      assertValid("valid/AgentWithFunctionToolOnPrivateMethod.java")
    }

    "reject Agent with multiple command handlers" in {
      assertInvalid(
        "invalid/AgentWithMultipleCommandHandlers.java",
        "has 2 command handlers",
        "There must be one public method returning Agent.Effect")
    }

    // Command handler validations
    "reject Agent without Effect method" in {
      assertInvalid(
        "invalid/AgentWithoutEffectMethod.java",
        "No public method returning akka.javasdk.agent.Agent.Effect, akka.javasdk.agent.Agent.StreamEffect found")
    }

    "accept Agent with inherited Effect method" in {
      assertValid("valid/AgentWithEffectMethodInherited.java")
    }

    "reject Agent with command handler having too many parameters" in {
      assertInvalid(
        "invalid/AgentWithTooManyParams.java",
        "Method [query] must have zero or one argument",
        "If you need to pass more arguments, wrap them in a class")
    }

    "reject Agent with @FunctionTool on command handler" in {
      assertInvalid(
        "invalid/AgentWithFunctionToolOnCommandHandler.java",
        "Agent command handler methods cannot be annotated with @FunctionTool.")
    }

    "reject Agent with both @AgentDescription and @Component name/description" in {
      assertInvalid(
        "invalid/AgentWithBothDescriptionAnnotations.java",
        "Both @AgentDescription.description and @Component.description are defined.",
        "Remove @AgentDescription.description and use only @Component.description",
        "Both @AgentDescription.name and @Component.name are defined.",
        "Remove @AgentDescription.name and use only @Component.name.")
    }

    "reject Agent with both @AgentDescription.role and @AgentRole" in {
      assertInvalid(
        "invalid/AgentWithBothRoleAnnotations.java",
        "Both @AgentDescription.role and @AgentRole are defined.",
        "Remove @AgentDescription.role and use only @AgentRole.")
    }

    "reject Agent with empty @AgentDescription.name (no @Component)" in {
      assertInvalid(
        "invalid/AgentWithEmptyAgentDescriptionName.java",
        "@AgentDescription.name is empty",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with empty @AgentDescription.description (no @Component)" in {
      assertInvalid(
        "invalid/AgentWithEmptyAgentDescriptionDescription.java",
        "@AgentDescription.description is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with blank @AgentDescription.name (no @Component)" in {
      assertInvalid(
        "invalid/AgentWithBlankAgentDescriptionName.java",
        "@AgentDescription.name is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }

    "reject Agent with blank @AgentDescription.description (no @Component)" in {
      assertInvalid(
        "invalid/AgentWithBlankAgentDescriptionDescription.java",
        "@AgentDescription.description is empty.",
        "Remove @AgentDescription annotation and use only @Component.")
    }
  }
}
