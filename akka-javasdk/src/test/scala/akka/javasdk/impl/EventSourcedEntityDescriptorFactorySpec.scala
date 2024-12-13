/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.InvalidEventSourcedEntityWithOverloadedCommandHandler
import org.scalatest.wordspec.AnyWordSpec

class EventSourcedEntityDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "The EventSourced entity descriptor factory" should {

    "validate an ESE must be declared as public" in {
      intercept[ValidationException] {
        Validations.validate(classOf[NotPublicComponents.NotPublicEventSourced]).failIfInvalid()
      }.getMessage should include(
        "NotPublicEventSourced is not marked with `public` modifier. Components must be public.")
    }

    "not allow overloaded command handlers" in {
      intercept[ValidationException] {
        Validations.validate(classOf[InvalidEventSourcedEntityWithOverloadedCommandHandler]).failIfInvalid()
      }.getMessage should include(
        "InvalidEventSourcedEntityWithOverloadedCommandHandler has 2 command handler methods named 'createUser'. Command handlers must have unique names.")
    }

  }

}
