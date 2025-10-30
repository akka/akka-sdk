/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.InvalidKeyValueEntityWithDuplicateHandlers
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.InvalidKeyValueEntityWithTwoArgCommandHandler
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.InvalidValueEntityWithOverloadedCommandHandler
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.KeyValueEntityWithFunctionToolOnNonEffectMethod
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.KeyValueEntityWithFunctionToolOnReadOnlyEffect
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.KeyValueEntityWithNoEffectMethod
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.KeyValueEntityWithValidFunctionTool
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.ValidKeyValueEntityWithNoArgCommandHandler
import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.ValidKeyValueEntityWithOneArgCommandHandler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KeyValueEntityValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "KeyValueEntity validation" should {

    "return Valid for KeyValueEntity with command handler with 0 arguments" in {
      val result = Validations.validate(classOf[ValidKeyValueEntityWithNoArgCommandHandler])
      result.isValid shouldBe true
    }

    "return Valid for KeyValueEntity with command handler with 1 argument" in {
      val result = Validations.validate(classOf[ValidKeyValueEntityWithOneArgCommandHandler])
      result.isValid shouldBe true
    }

    "return Invalid for KeyValueEntity with command handler with 2 arguments" in {
      Validations
        .validate(classOf[InvalidKeyValueEntityWithTwoArgCommandHandler])
        .expectInvalid("Method [execute] must have zero or one argument")
    }

    "return Invalid for KeyValueEntity with duplicate command handlers" in {
      Validations
        .validate(classOf[InvalidKeyValueEntityWithDuplicateHandlers])
        .expectInvalid("Command handlers must have unique names")
    }

    "return Invalid for KeyValueEntity with no methods returning an Effect" in {
      Validations
        .validate(classOf[KeyValueEntityWithNoEffectMethod])
        .expectInvalid("No method returning akka.javasdk.keyvalueentity.KeyValueEntity$Effect found")
    }

    "validate a KeyValueEntity must be declared as public" in {
      Validations
        .validate(classOf[NotPublicComponents.NotPublicValueEntity])
        .expectInvalid("NotPublicValueEntity is not marked with `public` modifier. Components must be public.")
    }

    "not allow overloaded command handlers" in {
      Validations
        .validate(classOf[InvalidValueEntityWithOverloadedCommandHandler])
        .expectInvalid(
          "InvalidValueEntityWithOverloadedCommandHandler has 2 command handler methods named 'createEntity'. Command handlers must have unique names.")
    }

    "return Invalid for KeyValueEntity with @FunctionTool on non-Effect method" in {
      Validations
        .validate(classOf[KeyValueEntityWithFunctionToolOnNonEffectMethod])
        .expectInvalid(
          "@FunctionTool can only be used on command handler methods returning KeyValueEntity.Effect or KeyValueEntity.ReadOnlyEffect")
    }

    "allow @FunctionTool on valid Effect method" in {
      Validations.validate(classOf[KeyValueEntityWithValidFunctionTool]).isValid shouldBe true
    }

    "allow @FunctionTool on ReadOnlyEffect method" in {
      Validations.validate(classOf[KeyValueEntityWithFunctionToolOnReadOnlyEffect]).isValid shouldBe true
    }
  }
}
