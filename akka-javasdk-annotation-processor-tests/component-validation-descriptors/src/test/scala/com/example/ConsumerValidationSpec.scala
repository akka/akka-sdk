/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeConsumerValidationSpec extends AbstractConsumerValidationSpec(CompileTimeValidation)
class RuntimeConsumerValidationSpec extends AbstractConsumerValidationSpec(RuntimeValidation)

abstract class AbstractConsumerValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with CompilationTestSupport {

  s"Consumer validation ($validationMode)" should {

    // Valid consumers
    "accept valid Consumer with topic subscription" in {
      assertValid("valid/ValidConsumer.java")
    }

    "accept valid Consumer with KeyValueEntity subscription" in {
      assertValid("valid/ValidConsumerWithKeyValueEntitySubscription.java")
    }

    "accept valid Consumer with EventSourcedEntity subscription" in {
      assertValid("valid/ValidConsumerWithESSubscription.java")
    }

    "accept valid Consumer with Workflow subscription" in {
      assertValid("valid/ValidConsumerWithWorkflowSubscription.java")
    }

    "accept valid Consumer with delete handler" in {
      assertValid("valid/ValidConsumerWithDeleteHandler.java")
    }

    "accept valid Consumer with topic publishing and valid source" in {
      assertValid("valid/ValidConsumerWithTopicPublishing.java")
    }

    "accept valid Consumer with stream subscription" in {
      assertValid("valid/ValidConsumerWithStreamSubscription.java")
    }

    "accept valid Consumer with stream publishing" in {
      assertValid("valid/ValidConsumerWithStreamPublishing.java")
    }

    "accept valid Consumer with raw event handler for EventSourcedEntity" in {
      assertValid("valid/ValidConsumerWithRawEventHandlerES.java")
    }

    "accept valid Consumer with raw event handler for KeyValueEntity" in {
      assertValid("valid/ValidConsumerWithRawEventHandlerKVE.java")
    }

    "accept valid Consumer with raw event handler for Workflow" in {
      assertValid("valid/ValidConsumerWithRawEventHandlerWorkflow.java")
    }

    // Invalid consumers - basic validations
    "reject Consumer without @Consume annotation" in {
      assertInvalid(
        "invalid/ConsumerWithoutConsumeAnnotation.java",
        "A Consumer must be annotated with `@Consume` annotation")
    }

    "reject Consumer without Effect methods" in {
      assertInvalid(
        "invalid/ConsumerWithoutEffectMethod.java",
        "No public method returning akka.javasdk.consumer.Consumer.Effect found")
    }

    "reject Consumer with command handler having too many parameters" in {
      assertInvalid("invalid/ConsumerWithTooManyParams.java", "must have zero or one argument")
    }

    "reject Consumer not declared as public" in {
      assertInvalid("invalid/NotPublicConsumer.java", "NotPublicConsumer is not marked with `public` modifier")
    }

    // Subscription validations
    "reject Consumer with multiple type-level subscriptions" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleSubscriptions.java",
        "Only one subscription type is allowed on a type level")
    }

    "reject Consumer with subscription method with no parameters (not delete handler)" in {
      assertInvalid(
        "invalid/ConsumerWithNoParamSubscriptionMethod.java",
        "Subscription method must have exactly one parameter",
        "unless it's marked with @DeleteHandler")
    }

    "reject Consumer with method level ACL on subscription method" in {
      assertInvalid(
        "invalid/ConsumerWithAclOnSubscriptionMethod.java",
        "Methods from classes annotated with Akka @Consume annotations are for internal use only")
    }

    // State subscription validations (KeyValueEntity and Workflow)
    "reject Consumer with multiple update methods for KeyValueEntity subscription" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleUpdateMethods.java",
        "Duplicated update methods [onUpdate1, onUpdate2]",
        "for state subscription are not allowed.")
    }

    "reject Consumer with multiple delete handlers" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleDeleteHandlers.java",
        "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject Consumer with delete handler with parameters" in {
      assertInvalid(
        "invalid/ConsumerWithDeleteHandlerWithParams.java",
        "Method annotated with '@DeleteHandler' must not have parameters")
    }

    "reject Consumer with multiple update methods for Workflow subscription" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleUpdateMethodsWorkflow.java",
        "Duplicated update methods [onUpdate1, onUpdate2]",
        "for state subscription are not allowed.")
    }

    "reject Consumer with multiple delete handlers for Workflow subscription" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleDeleteHandlersWorkflow.java",
        "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject Consumer with delete handler with parameters for Workflow subscription" in {
      assertInvalid(
        "invalid/ConsumerWithDeleteHandlerWithParamsWorkflow.java",
        "Method annotated with '@DeleteHandler' must not have parameters")
    }

    // Ambiguous handler validations
    "reject Consumer with ambiguous handlers for topic" in {
      assertInvalid(
        "invalid/ConsumerWithAmbiguousHandlers.java",
        "Ambiguous handlers for java.lang.String",
        "methods: [onMessage1, onMessage2] consume the same type")
    }

    "reject Consumer with ambiguous delete handlers" in {
      assertInvalid("invalid/ConsumerWithAmbiguousDeleteHandlers.java", "Ambiguous delete handlers")
    }

    "reject Consumer with ambiguous handlers for ValueEntity" in {
      assertInvalid(
        "invalid/AmbiguousHandlersVESubscriptionInConsumer.java",
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    "reject Consumer with ambiguous delete handlers for ValueEntity" in {
      assertInvalid(
        "invalid/AmbiguousDeleteHandlersVESubscriptionInConsumer.java",
        "Ambiguous delete handlers: [methodOne, methodTwo].")
    }

    "accept Consumer with with multiple private command handlers for EventSourcedEntity" in {
      assertValid("valid/PrivateHandlersESSubscriptionInConsumer.java")
    }

    "reject Consumer with ambiguous handlers for EventSourcedEntity" in {
      assertInvalid(
        "invalid/AmbiguousHandlersESSubscriptionInConsumer.java",
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    "reject Consumer with ambiguous handlers for ServiceStream (type level)" in {
      assertInvalid(
        "invalid/AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer.java",
        "Ambiguous handlers for java.lang.Integer",
        "methods: [methodOne, methodTwo] consume the same type")
    }

    // Publication validations
    "reject Consumer with topic publishing but no source" in {
      assertInvalid(
        "invalid/ConsumerWithTopicPublishingButNoSource.java",
        "You must select a source for @Produce.ToTopic",
        "Annotate this class with one a @Consume annotation")
    }

    "reject Consumer with empty stream ID in ServiceStream publishing" in {
      assertInvalid("invalid/ConsumerWithEmptyStreamId.java", "@Produce.ServiceStream id can not be an empty string")
    }

    // Missing handler validations
    "reject Consumer missing handler for KeyValueEntity subscription" in {
      assertInvalid("invalid/ConsumerMissingHandlerForKVE.java", "missing handlers")
    }

    "reject Consumer missing handler for Workflow subscription" in {
      assertInvalid("invalid/ConsumerMissingHandlerForWorkflow.java", "missing handlers")
    }

    "reject Consumer missing handler for EventSourcedEntity subscription" in {
      assertInvalid("invalid/ConsumerMissingHandlerForES.java", "missing an event handler")
    }

    "reject Consumer with @FunctionTool annotation" in {
      assertInvalid("invalid/ConsumerWithFunctionTool.java", "Consumer methods cannot be annotated with @FunctionTool.")
    }

    // SnapshotHandler validations
    "accept valid Consumer with @SnapshotHandler for EventSourcedEntity subscription" in {
      assertValid("valid/ValidConsumerWithSnapshotHandler.java")
    }

    "reject Consumer with @SnapshotHandler on KeyValueEntity subscription" in {
      assertInvalid(
        "invalid/ConsumerSnapshotHandlerWithKVE.java",
        "@SnapshotHandler can only be used in classes annotated with @Consume.FromEventSourcedEntity")
    }

    "reject Consumer with @SnapshotHandler on Topic subscription" in {
      assertInvalid(
        "invalid/ConsumerSnapshotHandlerWithTopic.java",
        "@SnapshotHandler can only be used in classes annotated with @Consume.FromEventSourcedEntity")
    }

    "reject Consumer with @SnapshotHandler on ServiceStream subscription with helpful message" in {
      assertInvalid(
        "invalid/ConsumerSnapshotHandlerWithServiceStream.java",
        "@SnapshotHandler cannot be used with @Consume.FromServiceStream",
        "define the @SnapshotHandler on the producer side")
    }

    "reject Consumer with multiple @SnapshotHandler methods" in {
      assertInvalid(
        "invalid/ConsumerWithMultipleSnapshotHandlers.java",
        "Only one method can be annotated with @SnapshotHandler")
    }

    "reject Consumer with @SnapshotHandler method with no parameters" in {
      assertInvalid(
        "invalid/ConsumerSnapshotHandlerNoParams.java",
        "@SnapshotHandler method must have exactly one parameter")
    }

    "reject Consumer with @SnapshotHandler method with too many parameters" in {
      assertInvalid(
        "invalid/ConsumerSnapshotHandlerTooManyParams.java",
        "@SnapshotHandler method must have exactly one parameter")
    }
  }
}
