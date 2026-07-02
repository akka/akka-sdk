/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeEvaluatorValidationSpec extends AbstractEvaluatorValidationSpec(CompileTimeValidation)
class RuntimeEvaluatorValidationSpec extends AbstractEvaluatorValidationSpec(RuntimeValidation)

abstract class AbstractEvaluatorValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"Evaluator validation ($validationMode)" should {

    "accept valid Evaluator with an @EvaluatesAgent binding" in {
      assertValid("valid/ValidEvaluator.java")
    }

    "reject Evaluator without any @EvaluatesAgent binding" in {
      assertInvalid("invalid/EvaluatorWithoutBinding.java", "An Evaluator must evaluate at least one agent")
    }

    "reject Evaluator with an empty @Component id" in {
      assertInvalid("invalid/EvaluatorWithEmptyComponentId.java", "@Component id is empty, must be a non-empty string")
    }
  }
}
