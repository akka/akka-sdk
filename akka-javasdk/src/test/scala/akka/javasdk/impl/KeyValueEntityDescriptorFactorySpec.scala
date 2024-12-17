/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.testmodels.keyvalueentity.ValueEntitiesTestModels.InvalidValueEntityWithOverloadedCommandHandler
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KeyValueEntityDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "ValueEntity descriptor factory" should {
    "validate a KeyValueEntity must be declared as public" in {
      intercept[ValidationException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicValueEntity]).failIfInvalid()
      }.getMessage should include(
        "NotPublicValueEntity is not marked with `public` modifier. Components must be public.")
    }

    "not allow overloaded command handlers" in {
      intercept[ValidationException] {
        Validations.validate(classOf[InvalidValueEntityWithOverloadedCommandHandler]).failIfInvalid()
      }.getMessage should include(
        "InvalidValueEntityWithOverloadedCommandHandler has 2 command handler methods named 'createEntity'. Command handlers must have unique names.")
    }

  }
}
