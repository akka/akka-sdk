/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.wordspec.AnyWordSpec

class ConsumerValidationSpec extends AnyWordSpec with CompilationTestSupport {

  "Consumer validation" should {

    // Valid consumers
    "accept valid Consumer with topic subscription" in {
      val result = compileTestSource("valid/ValidConsumer.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with KeyValueEntity subscription" in {
      val result = compileTestSource("valid/ValidConsumerWithKeyValueEntitySubscription.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with EventSourcedEntity subscription" in {
      val result = compileTestSource("valid/ValidConsumerWithESSubscription.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with Workflow subscription" in {
      val result = compileTestSource("valid/ValidConsumerWithWorkflowSubscription.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with delete handler" in {
      val result = compileTestSource("valid/ValidConsumerWithDeleteHandler.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with topic publishing and valid source" in {
      val result = compileTestSource("valid/ValidConsumerWithTopicPublishing.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with stream subscription" in {
      val result = compileTestSource("valid/ValidConsumerWithStreamSubscription.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with stream publishing" in {
      val result = compileTestSource("valid/ValidConsumerWithStreamPublishing.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with raw event handler for EventSourcedEntity" in {
      val result = compileTestSource("valid/ValidConsumerWithRawEventHandlerES.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with raw event handler for KeyValueEntity" in {
      val result = compileTestSource("valid/ValidConsumerWithRawEventHandlerKVE.java")
      assertCompilationSuccess(result)
    }

    "accept valid Consumer with raw event handler for Workflow" in {
      val result = compileTestSource("valid/ValidConsumerWithRawEventHandlerWorkflow.java")
      assertCompilationSuccess(result)
    }

    // Invalid consumers - basic validations
    "reject Consumer without @Consume annotation" in {
      val result = compileTestSource("invalid/ConsumerWithoutConsumeAnnotation.java")
      assertCompilationFailure(result, "A Consumer must be annotated with `@Consume` annotation")
    }

    "reject Consumer without Effect methods" in {
      val result = compileTestSource("invalid/ConsumerWithoutEffectMethod.java")
      assertCompilationFailure(result, "No method returning akka.javasdk.consumer.Consumer.Effect found")
    }

    "reject Consumer with command handler having too many parameters" in {
      val result = compileTestSource("invalid/ConsumerWithTooManyParams.java")
      assertCompilationFailure(result, "must have zero or one argument")
    }

    "reject Consumer not declared as public" in {
      val result = compileTestSource("invalid/NotPublicConsumer.java")
      assertCompilationFailure(result, "NotPublicConsumer is not marked with `public` modifier")
    }

    // Subscription validations
    "reject Consumer with multiple type-level subscriptions" in {
      val result = compileTestSource("invalid/ConsumerWithMultipleSubscriptions.java")
      assertCompilationFailure(result, "Only one subscription type is allowed on a type level")
    }

    "reject Consumer with subscription method with no parameters (not delete handler)" in {
      val result = compileTestSource("invalid/ConsumerWithNoParamSubscriptionMethod.java")
      assertCompilationFailure(
        result,
        "Subscription method must have exactly one parameter",
        "unless it's marked with @DeleteHandler")
    }

    "reject Consumer with method level ACL on subscription method" in {
      val result = compileTestSource("invalid/ConsumerWithAclOnSubscriptionMethod.java")
      assertCompilationFailure(
        result,
        "Methods from classes annotated with Akka @Consume annotations are for internal use only")
    }

    // State subscription validations (KeyValueEntity and Workflow)
    "reject Consumer with multiple update methods for KeyValueEntity subscription" in {
      val result = compileTestSource("invalid/ConsumerWithMultipleUpdateMethods.java")
      assertCompilationFailure(
        result,
        "Duplicated update methods [onUpdate1, onUpdate2]",
        "for state subscription are not allowed.")
    }

    "reject Consumer with multiple delete handlers" in {
      val result = compileTestSource("invalid/ConsumerWithMultipleDeleteHandlers.java")
      assertCompilationFailure(result, "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject Consumer with delete handler with parameters" in {
      val result = compileTestSource("invalid/ConsumerWithDeleteHandlerWithParams.java")
      assertCompilationFailure(result, "Method annotated with '@DeleteHandler' must not have parameters")
    }

    "reject Consumer with multiple update methods for Workflow subscription" in {
      val result = compileTestSource("invalid/ConsumerWithMultipleUpdateMethodsWorkflow.java")
      assertCompilationFailure(
        result,
        "Duplicated update methods [onUpdate1, onUpdate2]",
        "for state subscription are not allowed.")
    }

    "reject Consumer with multiple delete handlers for Workflow subscription" in {
      val result = compileTestSource("invalid/ConsumerWithMultipleDeleteHandlersWorkflow.java")
      assertCompilationFailure(result, "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject Consumer with delete handler with parameters for Workflow subscription" in {
      val result = compileTestSource("invalid/ConsumerWithDeleteHandlerWithParamsWorkflow.java")
      assertCompilationFailure(result, "Method annotated with '@DeleteHandler' must not have parameters")
    }

    // Ambiguous handler validations
    "reject Consumer with ambiguous handlers for topic" in {
      val result = compileTestSource("invalid/ConsumerWithAmbiguousHandlers.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for java.lang.String",
        "methods: [onMessage1, onMessage2] consume the same type")
    }

    "reject Consumer with ambiguous delete handlers" in {
      val result = compileTestSource("invalid/ConsumerWithAmbiguousDeleteHandlers.java")
      assertCompilationFailure(result, "Ambiguous delete handlers")
    }

    "reject Consumer with ambiguous handlers for ValueEntity" in {
      val result = compileTestSource("invalid/AmbiguousHandlersVESubscriptionInConsumer.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    "reject Consumer with ambiguous delete handlers for ValueEntity" in {
      val result = compileTestSource("invalid/AmbiguousDeleteHandlersVESubscriptionInConsumer.java")
      assertCompilationFailure(result, "Ambiguous delete handlers: [methodOne, methodTwo].")
    }

    "accept Consumer with with multiple private command handlers for EventSourcedEntity" in {
      val result = compileTestSource("valid/PrivateHandlersESSubscriptionInConsumer.java")
      assertCompilationSuccess(result)
    }

    "reject Consumer with ambiguous handlers for EventSourcedEntity" in {
      val result = compileTestSource("invalid/AmbiguousHandlersESSubscriptionInConsumer.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    "reject Consumer with ambiguous handlers for ServiceStream (type level)" in {
      val result = compileTestSource("invalid/AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    // Publication validations
    "reject Consumer with topic publishing but no source" in {
      val result = compileTestSource("invalid/ConsumerWithTopicPublishingButNoSource.java")
      assertCompilationFailure(
        result,
        "You must select a source for @Produce.ToTopic",
        "Annotate this class with one a @Consume annotation")
    }

    "reject Consumer with empty stream ID in ServiceStream publishing" in {
      val result = compileTestSource("invalid/ConsumerWithEmptyStreamId.java")
      assertCompilationFailure(result, "@Produce.ServiceStream id can not be an empty string")
    }

    // Missing handler validations
    "reject Consumer missing handler for KeyValueEntity subscription" in {
      val result = compileTestSource("invalid/ConsumerMissingHandlerForKVE.java")
      assertCompilationFailure(result, "missing handlers")
    }

    "reject Consumer missing handler for Workflow subscription" in {
      val result = compileTestSource("invalid/ConsumerMissingHandlerForWorkflow.java")
      assertCompilationFailure(result, "missing handlers")
    }

    "reject Consumer missing handler for EventSourcedEntity subscription" in {
      val result = compileTestSource("invalid/ConsumerMissingHandlerForES.java")
      assertCompilationFailure(result, "missing an event handler")
    }

    "reject Consumer with @FunctionTool on private methods" in {
      val result = compileTestSource("invalid/ConsumerWithFunctionToolOnPrivateMethod.java")
      assertCompilationFailure(
        result,
        "Methods annotated with @FunctionTool must be public. Private methods cannot be annotated with @FunctionTool")
    }
  }
}
