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
      assertCompilationFailure(result, "KeyValueEntityTwoArgs", "zero or one argument")
    }

    "reject KeyValueEntity with duplicate command handlers" in {
      val result = compileTestSource("invalid/KeyValueEntityDuplicateHandlers.java")
      assertCompilationFailure(result, "KeyValueEntityDuplicateHandlers", "unique names")
    }

    "reject KeyValueEntity with no Effect method" in {
      val result = compileTestSource("invalid/KeyValueEntityNoEffect.java")
      assertCompilationFailure(result, "KeyValueEntityNoEffect", "No method returning", "Effect")
    }

    "reject KeyValueEntity that is not public" in {
      val result = compileTestSource("invalid/NotPublicKeyValueEntity.java")
      assertCompilationFailure(result, "NotPublicKeyValueEntity", "not marked with `public` modifier")
    }

    "reject KeyValueEntity with overloaded command handlers" in {
      val result = compileTestSource("invalid/KeyValueEntityOverloaded.java")
      assertCompilationFailure(result, "KeyValueEntityOverloaded", "createEntity", "unique names")
    }
  }
}
