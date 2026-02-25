/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeEventSourcedEntityValidationSpec
    extends AbstractEventSourcedEntityValidationSpec(CompileTimeValidation)

class RuntimeEventSourcedEntityValidationSpec extends AbstractEventSourcedEntityValidationSpec(RuntimeValidation)

abstract class AbstractEventSourcedEntityValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"EventSourcedEntity validation ($validationMode)" should {

    "accept valid EventSourcedEntity ($label)" in {
      assertValid("valid/ValidEventSourcedEntity.java")
    }

    "reject EventSourcedEntity with non-sealed event type" in {
      assertInvalid(
        "invalid/NonSealedEventType.java",
        "The event type of an EventSourcedEntity is required to be a sealed interface")
    }

    "reject EventSourcedEntity with duplicate command handlers" in {
      assertInvalid("invalid/DuplicateCommandHandlers.java", "Command handlers must have unique names")
    }

    "reject EventSourcedEntity with command handler with 2 arguments" in {
      assertInvalid("invalid/CommandHandlerTwoArgs.java", "Method [create] must have zero or one argument")
    }

    "reject EventSourcedEntity with no Effect method" in {
      assertInvalid(
        "invalid/NoEffectMethod.java",
        "No public method returning akka.javasdk.eventsourcedentity.EventSourcedEntity.Effect found")
    }

    "accept EventSourcedEntity with inherited Effect method" in {
      assertValid("valid/ESEInheritedEffectMethod.java")
    }

    "reject EventSourcedEntity that is not public" in {
      assertInvalid(
        "invalid/NotPublicEventSourcedEntity.java",
        "NotPublicEventSourcedEntity is not marked with `public` modifier. Components must be public")
    }

    "accept EventSourcedEntity with @FunctionTool on Effect and ReadOnlyEffect" in {
      assertValid("valid/ValidEventSourcedEntityWithFunctionTool.java")
    }

    "reject EventSourcedEntity with @FunctionTool on non-Effect methods" in {
      assertInvalid(
        "invalid/EventSourcedEntityWithFunctionToolOnInvalidMethod.java",
        "EventSourcedEntity methods annotated with @FunctionTool must return Effect or ReadOnlyEffect")
    }

    "reject EventSourcedEntity with @FunctionTool on private methods" in {
      assertInvalid(
        "invalid/EventSourcedEntityWithFunctionToolOnPrivateMethod.java",
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }
  }
}
