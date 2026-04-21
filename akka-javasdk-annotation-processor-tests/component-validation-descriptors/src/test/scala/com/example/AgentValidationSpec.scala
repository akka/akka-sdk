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

  }
}
