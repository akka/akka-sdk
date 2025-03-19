/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.NotPublicComponents.NotPublicConsumer
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.keyvalueentity.CounterState
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousDeleteHandlersVESubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersESSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersESTypeLevelSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersTopiSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersTopicTypeLevelSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersVESubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersVETypeLevelSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMethodLevelAclAndSubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamPublishingConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingConsumeAnnotationConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingHandlersWhenSubscribeToEventSourcedEntityConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingSourceForTopicPublishing
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MultipleTypeLevelSubscriptions
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MultipleUpdateMethodsForVETypeLevelSubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToBytesFromTopic
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToEventSourcedEmployee
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToTopicTypeLevel
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToTopicTypeLevelCombined
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToValueEntityTypeLevel
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToValueEntityWithDeletes
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConsumerDescriptorFactorySpec extends AnyWordSpec with Matchers {

  "Consumer descriptor factory" should {

    "validate a Consumer must be declared as public" in {
      intercept[ValidationException] {
        Validations.validate(classOf[NotPublicConsumer]).failIfInvalid()
      }.getMessage should include("NotPublicConsumer is not marked with `public` modifier. Components must be public.")
    }

    "validate that Consumer must be annotated with Consume" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MissingConsumeAnnotationConsumer]).failIfInvalid()
      }.getMessage should include("A Consumer must be annotated with `@Consume` annotation.")
    }

    "generate mapping with Event Sourced Subscription annotations" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToEventSourcedEmployee], new JsonSerializer)

      // in case of @Migration, it should map 2 type urls to the same method
      desc.methodInvokers.view.mapValues(_.method.getName).toMap should
      contain only ("json.akka.io/created" -> "methodOne", "json.akka.io/old-created" -> "methodOne", "json.akka.io/emailUpdated" -> "methodTwo")
    }

    "generate mapping with Key Value Entity Subscription annotations (type level)" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToValueEntityTypeLevel], new JsonSerializer)

      // in case of @Migration, it should map 2 type urls to the same method
      desc.methodInvokers should have size 2
      desc.methodInvokers.view.mapValues(_.method.getName).toMap should
      contain only ("json.akka.io/counter-state" -> "onUpdate", "json.akka.io/" + classOf[
        CounterState].getName -> "onUpdate")
    }

    "generate mapping with Key Value Entity and delete handler" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToValueEntityWithDeletes], new JsonSerializer)

      desc.methodInvokers should have size 3
      desc.methodInvokers.view.mapValues(_.method.getName).toMap should
      contain only ("json.akka.io/akka.javasdk.testmodels.keyvalueentity.CounterState" -> "onUpdate", "json.akka.io/counter-state" -> "onUpdate", "type.googleapis.com/google.protobuf.Empty" -> "onDelete")
    }

    "generate mapping for a Consumer with a subscription to a topic (type level)" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToTopicTypeLevel], new JsonSerializer)
      desc.methodInvokers should have size 1
    }

    "generate mapping for a Consumer with a subscription to a topic (type level) combined" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToTopicTypeLevelCombined], new JsonSerializer)
      desc.methodInvokers should have size 3
      //TODO not sure why we need to support `json.akka.io/string` and `json.akka.io/java.lang.String`
      desc.methodInvokers.view.mapValues(_.method.getName).toMap should
      contain only ("json.akka.io/akka.javasdk.testmodels.Message" -> "messageOne", "json.akka.io/string" -> "messageTwo", "json.akka.io/java.lang.String" -> "messageTwo")
    }

    "validates that ambiguous handler VE" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersVESubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous delete handler VE" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousDeleteHandlersVESubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include("Ambiguous delete handlers: [methodOne, methodTwo].")
    }

    "validates that ambiguous handler VE (type level)" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersVETypeLevelSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that only single update handler is present for VE sub (type level)" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultipleUpdateMethodsForVETypeLevelSubscription]).failIfInvalid()
      }.getMessage should include("Duplicated update methods [methodOne, methodTwo] for state subscription are not allowed.")
    }

    "validates that only type level subscription is valid" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultipleTypeLevelSubscriptions]).failIfInvalid()
      }.getMessage should include("Only one subscription type is allowed on a type level.")
    }

    "validates that ambiguous handler ES" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersESSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler ES (type level)" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersESTypeLevelSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Stream (type level)" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Topic" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersTopiSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Topic (type level)" in {
      intercept[ValidationException] {
        Validations.validate(classOf[AmbiguousHandlersTopicTypeLevelSubscriptionInConsumer]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that source is missing for topic publication" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MissingSourceForTopicPublishing]).failIfInvalid()
      }.getMessage should include(
        "You must select a source for @Produce.ToTopic. Annotate this class with one a @Consume annotation.")
    }

    "validates if there are missing event handlers for event sourced Entity Subscription at type level" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MissingHandlersWhenSubscribeToEventSourcedEntityConsumer]).failIfInvalid()
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.subscriptions.PubSubTestModels$MissingHandlersWhenSubscribeToEventSourcedEntityConsumer': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "generate mapping for a Consumer with a VE subscription and publication to a topic" ignore {
      //TODO cover this with Spi tests
    }

    "generate mapping for a Consumer subscribing to raw bytes from a topic" in {
      val desc = ComponentDescriptor.descriptorFor(classOf[SubscribeToBytesFromTopic], new JsonSerializer)
      desc.methodInvokers.contains("type.kalix.io/bytes") shouldBe true
    }

    "generate mapping for a Consumer with a ES subscription and publication to a topic" ignore {
      //TODO cover this with Spi tests
    }

    "generate mapping for a Consumer with a Topic subscription and publication to a topic" ignore {
      //TODO cover this with Spi tests
    }

    "generate mapping for a Consumer with a Stream subscription and publication to a topic" ignore {
      //TODO cover this with Spi tests
    }

    "fail if it's subscription method exposed with ACL" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ConsumerWithMethodLevelAclAndSubscription]).failIfInvalid()
      }.getMessage should include(
        "Methods from classes annotated with Kalix @Consume annotations are for internal use only and cannot be annotated with ACL annotations.")
    }

    "generate mappings for service to service publishing " in {
      val desc = ComponentDescriptor.descriptorFor(classOf[EventStreamPublishingConsumer], new JsonSerializer)
      desc.methodInvokers.view.mapValues(_.method.getName).toMap should
      contain only ("json.akka.io/created" -> "transform", "json.akka.io/old-created" -> "transform", "json.akka.io/emailUpdated" -> "transform")
    }

    "generate mappings for service to service subscription " in {
      val desc = ComponentDescriptor.descriptorFor(classOf[EventStreamSubscriptionConsumer], new JsonSerializer)
      desc.methodInvokers should have size 3
    }
  }

}
