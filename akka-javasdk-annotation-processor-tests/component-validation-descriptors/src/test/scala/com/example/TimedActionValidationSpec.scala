/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class TimedActionValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "TimedAction validation" should {
    "accept valid TimedAction with Effect methods" in {
      val result = compileTestSource("valid/ValidTimedAction.java")
      assertCompilationSuccess(result)
    }

    "reject TimedAction without Effect methods" in {
      val result = compileTestSource("invalid/TimedActionWithoutEffect.java")
      assertCompilationFailure(result, "No public method returning akka.javasdk.timedaction.TimedAction.Effect found")
    }

    "reject TimedAction with command handler having too many parameters" in {
      val result = compileTestSource("invalid/TimedActionWithTooManyParams.java")
      assertCompilationFailure(result, "Method [invalidMethod] must have zero or one argument", "wrap them in a class")
    }

    "reject TimedAction with @FunctionTool annotation" in {
      val result = compileTestSource("invalid/TimedActionWithFunctionTool.java")
      assertCompilationFailure(result, "TimedAction methods cannot be annotated with @FunctionTool")
    }

    "return Valid for TimedAction with 0-arity method returning TimedAction.Effect" in {
      val result = compileTestSource("valid/ValidTimedActionNoArg.java")
      assertCompilationSuccess(result)
    }
    "return Valid for TimedAction with 1-arity method returning TimedAction.Effect" in {
      val result = compileTestSource("valid/ValidTimedActionOneArg.java")
      assertCompilationSuccess(result)
    }
  }
}
