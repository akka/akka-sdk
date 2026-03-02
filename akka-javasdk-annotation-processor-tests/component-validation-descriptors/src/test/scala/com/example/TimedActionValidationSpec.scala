/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeTimedActionValidationSpec extends AbstractTimedActionValidationSpec(CompileTimeValidation)
class RuntimeTimedActionValidationSpec extends AbstractTimedActionValidationSpec(RuntimeValidation)

abstract class AbstractTimedActionValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"TimedAction validation ($validationMode)" should {

    "accept valid TimedAction with Effect methods" in {
      assertValid("valid/ValidTimedAction.java")
    }

    "reject TimedAction without Effect methods" in {
      assertInvalid(
        "invalid/TimedActionWithoutEffect.java",
        "No public method returning akka.javasdk.timedaction.TimedAction.Effect found")
    }

    "reject TimedAction with command handler having too many parameters" in {
      assertInvalid(
        "invalid/TimedActionWithTooManyParams.java",
        "Method [invalidMethod] must have zero or one argument",
        "wrap them in a class")
    }

    "reject TimedAction with @FunctionTool annotation" in {
      assertInvalid(
        "invalid/TimedActionWithFunctionTool.java",
        "TimedAction methods cannot be annotated with @FunctionTool")
    }

    "return Valid for TimedAction with 0-arity method returning TimedAction.Effect" in {
      assertValid("valid/ValidTimedActionNoArg.java")
    }

    "return Valid for TimedAction with 1-arity method returning TimedAction.Effect" in {
      assertValid("valid/ValidTimedActionOneArg.java")
    }
  }
}
