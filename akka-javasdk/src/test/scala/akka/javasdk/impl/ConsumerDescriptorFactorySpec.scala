/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.keyvalueentity.CounterState
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamPublishingConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionConsumer
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
