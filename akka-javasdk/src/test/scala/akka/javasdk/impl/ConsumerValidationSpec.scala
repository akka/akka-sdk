/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl

import akka.javasdk.impl.NotPublicComponents.NotPublicConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousDeleteHandlersVESubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersESSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.AmbiguousHandlersVESubscriptionInConsumer
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerMissingHandlerForES
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerMissingHandlerForKVE
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerMissingHandlerForWorkflow
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithAmbiguousHandlers
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithDeleteHandlerWithParams
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithEmptyStreamId
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithFunctionTool
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMethodLevelAcl
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMethodWithNoParameters
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMultipleDeleteHandlers
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMultipleTypeLevelSubscriptions
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithMultipleUpdateMethods
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithTooManyArgsMethod
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithTopicPublishingButNoSource
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithValidDeleteHandler
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithoutConsumeAnnotation
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ConsumerWithoutHandler
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithESSubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithKVESubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithStreamPublishing
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithStreamSubscription
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithTopicPublishing
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.ValidConsumerWithTopicSubscription
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConsumerValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  "Consumer validation" should {

    "return Invalid for Consumer without @Consume annotation" in {
      Validations
        .validate(classOf[ConsumerWithoutConsumeAnnotation])
        .expectInvalid("A Consumer must be annotated with `@Consume` annotation")
    }

    "return Valid for Consumer with topic subscription" in {
      val result = Validations.validate(classOf[ValidConsumerWithTopicSubscription])
      result.isValid shouldBe true
    }

    "return Valid for Consumer with KeyValueEntity subscription" in {
      val result = Validations.validate(classOf[ValidConsumerWithKVESubscription])
      result.isValid shouldBe true
    }

    "return Valid for Consumer with EventSourcedEntity subscription" in {
      val result = Validations.validate(classOf[ValidConsumerWithESSubscription])
      result.isValid shouldBe true
    }

    "return Invalid for Consumer without a command handler" in {
      Validations
        .validate(classOf[ConsumerWithoutHandler])
        .expectInvalid("No method returning akka.javasdk.consumer.Consumer$Effect")
    }

    "return Invalid for Consumer with method having more than 1 argument" in {
      Validations
        .validate(classOf[ConsumerWithTooManyArgsMethod])
        .expectInvalid("must have zero or one argument")
    }

    "return Invalid for Consumer with ambiguous handlers" in {
      Validations
        .validate(classOf[ConsumerWithAmbiguousHandlers])
        .expectInvalid(
          "Ambiguous handlers for java.lang.String, methods: [handleOne, handleTwo] consume the same type.")
    }

    "return Invalid for Consumer with multiple type level subscriptions" in {
      Validations
        .validate(classOf[ConsumerWithMultipleTypeLevelSubscriptions])
        .expectInvalid("Only one subscription type is allowed on a type level")
    }

    "return Invalid for Consumer with multiple update methods for KeyValueEntity subscription" in {
      Validations.validate(classOf[ConsumerWithMultipleUpdateMethods]).expectInvalid("Duplicated update methods")
    }

    "return Invalid for Consumer with multiple delete handlers" in {
      Validations
        .validate(classOf[ConsumerWithMultipleDeleteHandlers])
        .expectInvalid("Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "return Valid for Consumer with valid delete handler" in {
      val result = Validations.validate(classOf[ConsumerWithValidDeleteHandler])
      result.isValid shouldBe true
    }

    "return Invalid for Consumer with delete handler with parameters" in {
      Validations
        .validate(classOf[ConsumerWithDeleteHandlerWithParams])
        .expectInvalid("Method annotated with '@DeleteHandler' must not have parameters")

    }

    "return Invalid for Consumer with topic publishing but no source" in {
      Validations
        .validate(classOf[ConsumerWithTopicPublishingButNoSource])
        .expectInvalid("You must select a source for @Produce.ToTopic")
    }

    "return Valid for Consumer with topic publishing and valid source" in {
      val result = Validations.validate(classOf[ValidConsumerWithTopicPublishing])
      result.isValid shouldBe true
    }

    "return Invalid for Consumer with method level ACL on subscription method" in {
      Validations
        .validate(classOf[ConsumerWithMethodLevelAcl])
        .expectInvalid("Methods from classes annotated with Akka @Consume annotations are for internal use only")

    }

    "return Invalid for Consumer missing handler for KeyValueEntity subscription" in {
      Validations.validate(classOf[ConsumerMissingHandlerForKVE]).expectInvalid("missing handlers")
    }

    "return Invalid for Consumer missing handler for Workflow subscription" in {
      Validations.validate(classOf[ConsumerMissingHandlerForWorkflow]).expectInvalid("missing handlers")
    }

    "return Invalid for Consumer missing handler for EventSourcedEntity subscription" in {
      Validations.validate(classOf[ConsumerMissingHandlerForES]).expectInvalid("missing an event handler")
    }

    "return Invalid for Consumer with subscription method with no parameters (not delete handler)" in {
      Validations
        .validate(classOf[ConsumerWithMethodWithNoParameters])
        .expectInvalid("Subscription method must have exactly one parameter")
    }

    "return Valid for Consumer with stream subscription" in {
      val result = Validations.validate(classOf[ValidConsumerWithStreamSubscription])
      result.isValid shouldBe true
    }

    "return Invalid for Consumer with empty stream ID in ServiceStream publishing" in {
      Validations
        .validate(classOf[ConsumerWithEmptyStreamId])
        .expectInvalid("@Produce.ServiceStream id can not be an empty string")
    }

    "return Valid for Consumer with valid stream publishing" in {
      val result = Validations.validate(classOf[ValidConsumerWithStreamPublishing])
      result.isValid shouldBe true
    }

    "return Invalid for Consumer not declared as public" in {
      Validations
        .validate(classOf[NotPublicConsumer])
        .expectInvalid("NotPublicConsumer is not marked with `public` modifier. Components must be public.")
    }

    "return Invalid for Consumer with ambiguous handlers for ValueEntity" in {
      Validations
        .validate(classOf[AmbiguousHandlersVESubscriptionInConsumer])
        .expectInvalid(
          "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "return Invalid for Consumer with ambiguous delete handlers for ValueEntity" in {
      Validations
        .validate(classOf[AmbiguousDeleteHandlersVESubscriptionInConsumer])
        .expectInvalid("Ambiguous delete handlers: [methodOne, methodTwo].")
    }

    "return Invalid for Consumer with ambiguous handlers for EventSourcedEntity" in {
      Validations
        .validate(classOf[AmbiguousHandlersESSubscriptionInConsumer])
        .expectInvalid(
          "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "return Invalid for Consumer with ambiguous handlers for ServiceStream (type level)" in {
      Validations
        .validate(classOf[AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer])
        .expectInvalid(
          "Ambiguous handlers for java.lang.Integer, methods: [methodOne, methodTwo] consume the same type.")
    }

    "return Invalid for Consumer with @FunctionTool annotation" in {
      Validations
        .validate(classOf[ConsumerWithFunctionTool])
        .expectInvalid("@FunctionTool cannot be used in Consumer components")
    }

  }
}
