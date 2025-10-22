/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EventSourcedEntityValidationSpec extends AnyWordSpec with Matchers with CompilationTestSupport {

  "EventSourcedEntity validation" should {

    "accept valid EventSourcedEntity" in {
      val result = compileTestSource("valid/ValidEventSourcedEntity.java")
      assertCompilationSuccess(result)
    }

    "reject EventSourcedEntity with non-sealed event type" in {
      val result = compileTestSource("invalid/NonSealedEventType.java")
      assertCompilationFailure(result, "NonSealedEventType", "event type", "sealed")
    }

    "reject EventSourcedEntity with duplicate command handlers" in {
      val result = compileTestSource("invalid/DuplicateCommandHandlers.java")
      assertCompilationFailure(result, "DuplicateCommandHandlers", "command handler", "unique")
    }

    "reject EventSourcedEntity with command handler with 2 arguments" in {
      val result = compileTestSource("invalid/CommandHandlerTwoArgs.java")
      assertCompilationFailure(result, "CommandHandlerTwoArgs", "zero or one argument")
    }

    "reject EventSourcedEntity with no Effect method" in {
      val result = compileTestSource("invalid/NoEffectMethod.java")
      assertCompilationFailure(result, "NoEffectMethod", "No method returning", "Effect")
    }

    "reject EventSourcedEntity that is not public" in {
      val result = compileTestSource("invalid/NotPublicEventSourcedEntity.java")
      assertCompilationFailure(result, "NotPublicEventSourcedEntity", "not marked with `public` modifier")
    }
  }
}
