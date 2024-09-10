/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.InvalidComponentException
import akka.javasdk.impl.Validations
import NotPublicComponents.NotPublicConsumer
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
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ESWithPublishToTopicConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamPublishingConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingConsumeAnnotationConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingHandlersWhenSubscribeToEventSourcedEntityConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MissingSourceForTopicPublishing
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MultipleTypeLevelSubscriptions
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.MultipleUpdateMethodsForVETypeLevelSubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.PublishBytesToTopic
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.StreamSubscriptionWithPublishToTopic
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeOnlyOneToEventSourcedEntity
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToBytesFromTopic
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToEventSourcedEmployee
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToEventSourcedEntity
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToTopicCombined
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToTopicTypeLevel
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToTopicTypeLevelCombined
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToValueEntityTypeLevel
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeToValueEntityWithDeletes
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.TypeLevelTopicSubscriptionWithPublishToTopic
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.VEWithPublishToTopic
import com.google.protobuf.BytesValue
import com.google.protobuf.empty.Empty
import com.google.protobuf.{ Any => JavaPbAny }
import org.scalatest.wordspec.AnyWordSpec

class ConsumerDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "Consumer descriptor factory" should {

    "validate a Consumer must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicConsumer]).failIfInvalid
      }.getMessage should include("NotPublicConsumer is not marked with `public` modifier. Components must be public.")
    }

    "validate that Consumer must be annotated with Consume" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MissingConsumeAnnotationConsumer]).failIfInvalid
      }.getMessage should include("A Consumer must be annotated with `@Consume` annotation.")
    }

    "generate mapping with Event Sourced Subscription annotations" in {
      assertDescriptor[SubscribeToEventSourcedEmployee] { desc =>

        val onUpdateMethodDescriptor = findMethodByName(desc, "KalixSyntheticMethodOnESEmployee")
        onUpdateMethodDescriptor.isServerStreaming shouldBe false
        onUpdateMethodDescriptor.isClientStreaming shouldBe false

        val onUpdateMethod = desc.commandHandlers("KalixSyntheticMethodOnESEmployee")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventing = findKalixServiceOptions(desc).getEventing.getIn
        eventing.getEventSourcedEntity shouldBe "employee"

        // in case of @Migration, it should map 2 type urls to the same method
        onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
        contain only ("json.kalix.io/created" -> "methodOne", "json.kalix.io/old-created" -> "methodOne", "json.kalix.io/emailUpdated" -> "methodTwo")
      }
    }

    "generate combined mapping with Event Sourced Entity Subscription annotation" in {
      assertDescriptor[SubscribeToEventSourcedEntity] { desc =>
        val methodDescriptor = findMethodByName(desc, "KalixSyntheticMethodOnESCounterentity")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false

        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnESCounterentity")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventSourceOne = findKalixServiceOptions(desc).getEventing.getIn
        eventSourceOne.getEventSourcedEntity shouldBe "counter-entity"
      }
    }

    "generate mapping with Key Value Entity Subscription annotations (type level)" in {
      assertDescriptor[SubscribeToValueEntityTypeLevel] { desc =>

        val onUpdateMethodDescriptor = findMethodByName(desc, "OnUpdate")
        onUpdateMethodDescriptor.isServerStreaming shouldBe false
        onUpdateMethodDescriptor.isClientStreaming shouldBe false

        val onUpdateMethod = desc.commandHandlers("OnUpdate")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventing = findKalixMethodOptions(onUpdateMethodDescriptor).getEventing.getIn
        eventing.getValueEntity shouldBe "ve-counter"

        // in case of @Migration, it should map 2 type urls to the same method
        onUpdateMethod.methodInvokers should have size 2
        onUpdateMethod.methodInvokers.values.map { javaMethod =>
          javaMethod.parameterExtractors.length shouldBe 1
        }
        onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
        contain only ("json.kalix.io/counter-state" -> "onUpdate", "json.kalix.io/" + classOf[
          CounterState].getName -> "onUpdate")
      }
    }

    "generate mapping with Key Value Entity and delete handler" in {
      assertDescriptor[SubscribeToValueEntityWithDeletes] { desc =>

        val onUpdateMethodDescriptor = findMethodByName(desc, "OnUpdate")
        onUpdateMethodDescriptor.isServerStreaming shouldBe false
        onUpdateMethodDescriptor.isClientStreaming shouldBe false

        val onUpdateMethod = desc.commandHandlers("OnUpdate")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventing = findKalixMethodOptions(onUpdateMethodDescriptor).getEventing.getIn
        eventing.getValueEntity shouldBe "ve-counter"
        eventing.getHandleDeletes shouldBe false

        val onDeleteMethodDescriptor = findMethodByName(desc, "OnDelete")
        onDeleteMethodDescriptor.isServerStreaming shouldBe false
        onDeleteMethodDescriptor.isClientStreaming shouldBe false

        val onDeleteMethod = desc.commandHandlers("OnDelete")
        onDeleteMethod.requestMessageDescriptor.getFullName shouldBe Empty.javaDescriptor.getFullName

        val deleteEventing = findKalixMethodOptions(onDeleteMethodDescriptor).getEventing.getIn
        deleteEventing.getValueEntity shouldBe "ve-counter"
        deleteEventing.getHandleDeletes shouldBe true
      }
    }

    "generate mapping for a Consumer with a subscription to a topic (type level)" in {
      assertDescriptor[SubscribeToTopicTypeLevel] { desc =>
        val methodOne = desc.commandHandlers("MessageOne")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventSourceOne = findKalixMethodOptions(desc, "MessageOne").getEventing.getIn
        eventSourceOne.getTopic shouldBe "topicXYZ"
        eventSourceOne.getConsumerGroup shouldBe "cg"

        // should have a default extractor for any payload
        val javaMethod = methodOne.methodInvokers.values.head
        javaMethod.parameterExtractors.length shouldBe 1
      }
    }

    "generate mapping for a Consumer with a subscription to a topic (type level) with combined handler" in {
      assertDescriptor[SubscribeToTopicTypeLevelCombined] { desc =>
        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnTopicTopicXYZ")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val topicSource = findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicTopicXYZ").getEventing.getIn
        topicSource.getTopic shouldBe "topicXYZ"
        topicSource.getConsumerGroup shouldBe "cg"
        // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
        topicSource.getIgnore shouldBe false
        topicSource.getIgnoreUnknown shouldBe false

        // should have a default extractor for any payload
        val javaMethod = methodOne.methodInvokers.values.head
        javaMethod.parameterExtractors.length shouldBe 1
      }
    }

    "generate mapping for a Consumer with a subscription to a topic with combined handler" in {
      assertDescriptor[SubscribeToTopicCombined] { desc =>
        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnTopicTopicXYZ")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventSourceOne = findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicTopicXYZ").getEventing.getIn
        eventSourceOne.getTopic shouldBe "topicXYZ"
        eventSourceOne.getConsumerGroup shouldBe "cg"

        // should have a default extractor for any payload
        val javaMethod = methodOne.methodInvokers.values.head
        javaMethod.parameterExtractors.length shouldBe 1
      }
    }

    "generate mapping with Event Sourced Entity Subscription annotation type level with only one method" in {
      assertDescriptor[SubscribeOnlyOneToEventSourcedEntity] { desc =>
        val methodDescriptor = findMethodByName(desc, "MethodOne")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false

        val methodOne = desc.commandHandlers("MethodOne")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventSourceOne = findKalixServiceOptions(desc).getEventing.getIn
        eventSourceOne.getEventSourcedEntity shouldBe "counter-entity"
        // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
        eventSourceOne.getIgnore shouldBe false
        eventSourceOne.getIgnoreUnknown shouldBe false
      }
    }

    "validates that ambiguous handler VE" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersVESubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous delete handler VE" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousDeleteHandlersVESubscriptionInConsumer]).failIfInvalid
      }.getMessage should include("Ambiguous delete handlers: [methodOne, methodTwo].")
    }

    "validates that ambiguous handler VE (type level)" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersVETypeLevelSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that only single update handler is present for VE sub (type level)" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultipleUpdateMethodsForVETypeLevelSubscription]).failIfInvalid
      }.getMessage should include("Duplicated update methods [methodOne, methodTwo]for KeyValueEntity subscription.")
    }

    "validates that only type level subscription is valid" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultipleTypeLevelSubscriptions]).failIfInvalid
      }.getMessage should include("Only one subscription type is allowed on a type level.")
    }

    "validates that ambiguous handler ES" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersESSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler ES (type level)" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersESTypeLevelSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Stream (type level)" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Topic" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersTopiSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that ambiguous handler Topic (type level)" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[AmbiguousHandlersTopicTypeLevelSubscriptionInConsumer]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "validates that source is missing for topic publication" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MissingSourceForTopicPublishing]).failIfInvalid
      }.getMessage should include(
        "You must select a source for @Produce.ToTopic. Annotate this class with one a @Consume annotation.")
    }

    "validates if there are missing event handlers for event sourced Entity Subscription at type level" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MissingHandlersWhenSubscribeToEventSourcedEntityConsumer]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.subscriptions.PubSubTestModels$MissingHandlersWhenSubscribeToEventSourcedEntityConsumer': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "generate mapping for a Consumer with a VE subscription and publication to a topic" in {
      assertDescriptor[VEWithPublishToTopic] { desc =>
        val methodOne = desc.commandHandlers("MessageOne")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventDestinationOne = findKalixMethodOptions(desc, "MessageOne").getEventing.getOut
        eventDestinationOne.getTopic shouldBe "foobar"

        // should have a default extractor for any payload
        val javaMethodOne = methodOne.methodInvokers.values.head
        javaMethodOne.parameterExtractors.length shouldBe 1

        val methodTwo = desc.commandHandlers("MessageTwo")
        methodTwo.requestMessageDescriptor.getFullName shouldBe Empty.javaDescriptor.getFullName

        val eventDestinationTwo = findKalixMethodOptions(desc, "MessageTwo").getEventing.getOut
        eventDestinationTwo.getTopic shouldBe "foobar"

        // delete handler with 0 params
        val javaMethodTwo = methodTwo.methodInvokers.values.head
        javaMethodTwo.parameterExtractors.length shouldBe 0
      }
    }

    "generate mapping for a Consumer with raw bytes publication to a topic" in {
      assertDescriptor[PublishBytesToTopic] { desc =>
        val methodOne = desc.commandHandlers("Produce")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val methodDescriptor = findMethodByName(desc, "Produce")
        methodDescriptor.getInputType.getFullName shouldBe JavaPbAny.getDescriptor.getFullName
        methodDescriptor.getOutputType.getFullName shouldBe JavaPbAny.getDescriptor.getFullName
      }
    }

    "generate mapping for a Consumer subscribing to raw bytes from a topic" in {
      assertDescriptor[SubscribeToBytesFromTopic] { desc =>
        val methodOne = desc.commandHandlers("Consume")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        methodOne.methodInvokers.contains("type.kalix.io/bytes") shouldBe true

        val methodDescriptor = findMethodByName(desc, "Consume")
        methodDescriptor.getInputType.getFullName shouldBe BytesValue.getDescriptor.getFullName
        methodDescriptor.getOutputType.getFullName shouldBe JavaPbAny.getDescriptor.getFullName
      }
    }

    "generate mapping for a Consumer with a ES subscription and publication to a topic" in {
      assertDescriptor[ESWithPublishToTopicConsumer] { desc =>
        desc.commandHandlers should have size 1

        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnESEmployee")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventDestinationOne = findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee").getEventing.getOut
        eventDestinationOne.getTopic shouldBe "foobar"
      }
    }

    "generate mapping for a Consumer with a Topic subscription and publication to a topic" in {
      assertDescriptor[TypeLevelTopicSubscriptionWithPublishToTopic] { desc =>
        desc.commandHandlers should have size 1

        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnTopicSource")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventDestinationOne = findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource").getEventing.getOut
        eventDestinationOne.getTopic shouldBe "foobar"
      }
    }

    "generate mapping for a Consumer with a Topic type level subscription and publication to a topic" in {
      assertDescriptor[TypeLevelTopicSubscriptionWithPublishToTopic] { desc =>
        desc.commandHandlers should have size 1

        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnTopicSource")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventDestinationOne = findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource").getEventing.getOut
        eventDestinationOne.getTopic shouldBe "foobar"
      }
    }

    "generate mapping for a Consumer with a Stream subscription and publication to a topic" in {
      assertDescriptor[StreamSubscriptionWithPublishToTopic] { desc =>
        desc.commandHandlers should have size 1

        val methodOne = desc.commandHandlers("KalixSyntheticMethodOnStreamSource")
        methodOne.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventDestinationOne = findKalixMethodOptions(desc, "KalixSyntheticMethodOnStreamSource").getEventing.getOut
        eventDestinationOne.getTopic shouldBe "foobar"
      }
    }

    "fail if it's subscription method exposed with ACL" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ConsumerWithMethodLevelAclAndSubscription]).failIfInvalid
      }.getMessage should include(
        "Methods from classes annotated with Kalix @Consume annotations are for internal use only and cannot be annotated with ACL annotations.")
    }

    "generate mappings for service to service publishing " in {
      assertDescriptor[EventStreamPublishingConsumer] { desc =>
        val serviceOptions = findKalixServiceOptions(desc)

        val eventingOut = serviceOptions.getEventing.getOut
        eventingOut.getDirect.getEventStreamId shouldBe "employee_events"

        val methodDescriptor = findMethodByName(desc, "KalixSyntheticMethodOnESEmployee")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false

        val eventingIn = serviceOptions.getEventing.getIn
        val entityType = eventingIn.getEventSourcedEntity
        entityType shouldBe "employee"

        val onUpdateMethod = desc.commandHandlers("KalixSyntheticMethodOnESEmployee")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
        contain only ("json.kalix.io/created" -> "transform", "json.kalix.io/old-created" -> "transform", "json.kalix.io/emailUpdated" -> "transform")
      }
    }

    "generate mappings for service to service subscription " in {
      assertDescriptor[EventStreamSubscriptionConsumer] { desc =>
        val serviceOptions = findKalixServiceOptions(desc)

        val eventingIn = serviceOptions.getEventing.getIn
        val eventingInDirect = eventingIn.getDirect
        eventingInDirect.getService shouldBe "employee_service"
        eventingInDirect.getEventStreamId shouldBe "employee_events"

        // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
        eventingIn.getIgnore shouldBe false
        eventingIn.getIgnoreUnknown shouldBe false

        val methodDescriptor = findMethodByName(desc, "KalixSyntheticMethodOnStreamEmployeeevents")
        methodDescriptor.isServerStreaming shouldBe false
        methodDescriptor.isClientStreaming shouldBe false
      }
    }
  }

}
