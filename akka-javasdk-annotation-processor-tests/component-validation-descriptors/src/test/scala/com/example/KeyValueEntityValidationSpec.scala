/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KeyValueEntityValidationSpec extends AnyWordSpec with Matchers with CompilationTestSupport {

  "KeyValueEntity validation" should {

    "accept valid KeyValueEntity with 0-arg command handler" in {
      val result = compileTestSource("valid/ValidKeyValueEntityNoArg.java")
      assertCompilationSuccess(result)
    }

    "accept valid KeyValueEntity with 1-arg command handler" in {
      val result = compileTestSource("valid/ValidKeyValueEntityOneArg.java")
      assertCompilationSuccess(result)
    }

    "reject KeyValueEntity with command handler with 2 arguments" in {
      val result = compileTestSource("invalid/KeyValueEntityTwoArgs.java")
      assertCompilationFailure(result, "Method [execute] must have zero or one argument")
    }

    "reject KeyValueEntity with duplicate command handlers" in {
      val result = compileTestSource("invalid/KeyValueEntityDuplicateHandlers.java")
      assertCompilationFailure(result, "Command handlers must have unique names")
    }

    "reject KeyValueEntity with no Effect method" in {
      val result = compileTestSource("invalid/KeyValueEntityNoEffect.java")
      assertCompilationFailure(result, "No method returning akka.javasdk.keyvalueentity.KeyValueEntity.Effect found")
    }

    "reject KeyValueEntity that is not public" in {
      val result = compileTestSource("invalid/NotPublicKeyValueEntity.java")
      assertCompilationFailure(
        result,
        "NotPublicKeyValueEntity is not marked with `public` modifier. Components must be public")
    }

    "reject KeyValueEntity with overloaded command handlers" in {
      val result = compileTestSource("invalid/KeyValueEntityOverloaded.java")
      assertCompilationFailure(
        result,
        "KeyValueEntityOverloaded has 2 command handler methods named 'createEntity'. Command handlers must have unique names.")
    }

    "accept KeyValueEntity with @FunctionTool on Effect and ReadOnlyEffect" in {
      val result = compileTestSource("valid/ValidKeyValueEntityWithFunctionTool.java")
      assertCompilationSuccess(result)
    }

    "reject KeyValueEntity with @FunctionTool on non-Effect methods" in {
      val result = compileTestSource("invalid/KeyValueEntityWithFunctionToolOnInvalidMethod.java")
      assertCompilationFailure(
        result,
        "KeyValueEntity methods annotated with @FunctionTool must return Effect or ReadOnlyEffect")
    }
  }
}
