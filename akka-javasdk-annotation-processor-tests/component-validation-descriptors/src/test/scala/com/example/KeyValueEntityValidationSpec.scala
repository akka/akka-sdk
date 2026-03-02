/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeKeyValueEntityValidationSpec extends AbstractKeyValueEntityValidationSpec(CompileTimeValidation)
class RuntimeKeyValueEntityValidationSpec extends AbstractKeyValueEntityValidationSpec(RuntimeValidation)

abstract class AbstractKeyValueEntityValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"KeyValueEntity validation ($validationMode)" should {

    "accept valid KeyValueEntity with 0-arg command handler" in {
      assertValid("valid/ValidKeyValueEntityNoArg.java")
    }

    "accept valid KeyValueEntity with 1-arg command handler" in {
      assertValid("valid/ValidKeyValueEntityOneArg.java")
    }

    "reject KeyValueEntity with command handler with 2 arguments" in {
      assertInvalid("invalid/KeyValueEntityTwoArgs.java", "Method [execute] must have zero or one argument")
    }

    "reject KeyValueEntity with duplicate command handlers" in {
      assertInvalid("invalid/KeyValueEntityDuplicateHandlers.java", "Command handlers must have unique names")
    }

    "reject KeyValueEntity with no Effect method" in {
      assertInvalid(
        "invalid/KeyValueEntityNoEffect.java",
        "No public method returning akka.javasdk.keyvalueentity.KeyValueEntity.Effect found")
    }

    "reject KeyValueEntity that is not public" in {
      assertInvalid(
        "invalid/NotPublicKeyValueEntity.java",
        "NotPublicKeyValueEntity is not marked with `public` modifier. Components must be public")
    }

    "reject KeyValueEntity with overloaded command handlers" in {
      assertInvalid(
        "invalid/KeyValueEntityOverloaded.java",
        "KeyValueEntityOverloaded has 2 command handler methods named 'createEntity'. Command handlers must have unique names.")
    }

    "accept KeyValueEntity with @FunctionTool on Effect and ReadOnlyEffect" in {
      assertValid("valid/ValidKeyValueEntityWithFunctionTool.java")
    }

    "reject KeyValueEntity with @FunctionTool on non-Effect methods" in {
      assertInvalid(
        "invalid/KeyValueEntityWithFunctionToolOnInvalidMethod.java",
        "KeyValueEntity methods annotated with @FunctionTool must return Effect or ReadOnlyEffect")
    }

    "reject KeyValueEntity with @FunctionTool on private methods" in {
      assertInvalid(
        "invalid/KeyValueEntityWithFunctionToolOnPrivateMethod.java",
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }
  }
}
