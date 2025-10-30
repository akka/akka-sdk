/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.NotPublicComponents.NotPublicAction
import akka.javasdk.testmodels.action.ActionsTestModels.NotTimedAction
import akka.javasdk.testmodels.action.ActionsTestModels.TimedActionWithFunctionTool
import akka.javasdk.testmodels.action.ActionsTestModels.TimedActionWithNoArgMethod
import akka.javasdk.testmodels.action.ActionsTestModels.TimedActionWithNoEffectMethod
import akka.javasdk.testmodels.action.ActionsTestModels.TimedActionWithSingleArgMethod
import akka.javasdk.testmodels.action.ActionsTestModels.TimedActionWithTooManyArgsMethod
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class TimedActionValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "TimedAction validation" should {

    "return Valid for TimedAction with 0-arity method returning TimedAction.Effect" in {
      val result = Validations.validate(classOf[TimedActionWithNoArgMethod])
      result.isValid shouldBe true
    }

    "return Valid for TimedAction with 1-arity method returning TimedAction.Effect" in {
      val result = Validations.validate(classOf[TimedActionWithSingleArgMethod])
      result.isValid shouldBe true
    }

    "return Invalid for TimedAction with >1-arity method returning TimedAction.Effect" in {
      Validations
        .validate(classOf[TimedActionWithTooManyArgsMethod])
        .expectInvalid("Method [foo] must have zero or one argument")
    }

    "return Invalid for TimedAction with no methods returning TimedAction.Effect" in {
      Validations
        .validate(classOf[TimedActionWithNoEffectMethod])
        .expectInvalid("No method returning akka.javasdk.timedaction.TimedAction$Effect found")
    }

    "return Valid for a class not annotated with TimedAction" in {
      val result = Validations.validate(classOf[NotTimedAction])
      result.isValid shouldBe true
    }

    "validate an Action must be declared as public" in {
      Validations
        .validate(classOf[NotPublicAction])
        .expectInvalid("NotPublicAction is not marked with `public` modifier. Components must be public.")
    }

    "return Invalid for TimedAction with @FunctionTool annotation" in {
      Validations
        .validate(classOf[TimedActionWithFunctionTool])
        .expectInvalid("@FunctionTool cannot be used in TimedAction components")
    }
  }
}
