/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EntityWithDuplicateCommandHandlers
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EntityWithNoEffectMethod
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EntityWithNonSealedEvent
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EntityWithTwoArgCommandHandler
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.InvalidEventSourcedEntityWithOverloadedCommandHandler
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.ValidEntity
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EventSourcedEntityValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "EventSourcedEntity validation" should {

    "return Valid for a valid EventSourcedEntity" in {
      val result = Validations.validate(classOf[ValidEntity])
      result.isValid shouldBe true
    }

    "return Invalid for EventSourcedEntity with a non-sealed event type" in {
      Validations
        .validate(classOf[EntityWithNonSealedEvent])
        .expectInvalid("is required to be a sealed interface")
    }

    "return Invalid for EventSourcedEntity with duplicate command handlers" in {
      Validations
        .validate(classOf[EntityWithDuplicateCommandHandlers])
        .expectInvalid("Command handlers must have unique names")
    }

    "return Invalid for EventSourcedEntity with a command handler with 2 arguments" in {
      Validations
        .validate(classOf[EntityWithTwoArgCommandHandler])
        .expectInvalid("Method [command] must have zero or one argument")
    }

    "return Invalid for EventSourcedEntity with no methods returning an Effect" in {
      Validations
        .validate(classOf[EntityWithNoEffectMethod])
        .expectInvalid("No method returning akka.javasdk.eventsourcedentity.EventSourcedEntity$Effect found")
    }

    "return Invalid for EventSourcedEntity with overloaded command handlers" in {
      Validations
        .validate(classOf[InvalidEventSourcedEntityWithOverloadedCommandHandler])
        .expectInvalid("Command handlers must have unique names")
    }

    "return Invalid for EventSourcedEntity that is not declared as public" in {
      Validations
        .validate(classOf[NotPublicComponents.NotPublicEventSourced])
        .expectInvalid("is not marked with `public` modifier. Components must be public")
    }
  }
}
