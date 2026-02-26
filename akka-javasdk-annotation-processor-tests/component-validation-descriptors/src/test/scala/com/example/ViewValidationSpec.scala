/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import com.example.CompilationTestSupport.CompileTimeValidation
import com.example.CompilationTestSupport.RuntimeValidation
import com.example.CompilationTestSupport.ValidationMode
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CompileTimeViewValidationSpec extends AbstractViewValidationSpec(CompileTimeValidation)
class RuntimeViewValidationSpec extends AbstractViewValidationSpec(RuntimeValidation)

abstract class AbstractViewValidationSpec(val validationMode: ValidationMode)
    extends AnyWordSpec
    with Matchers
    with CompilationTestSupport {

  s"View validation ($validationMode)" should {

    "accept valid View" in {
      assertValid("valid/ValidView.java")
    }

    "accept View with two queries" in {
      assertValid("valid/ViewWithTwoQueries.java")
    }

    "accept multi-table View with multiple queries" in {
      assertValid("valid/MultiTableViewWithMultipleQueries.java")
    }

    "accept View with empty TableUpdater for KeyValueEntity passthrough scenario" in {
      assertValid("valid/ValidViewWithEmptyTableUpdater.java")
    }

    "accept View with empty TableUpdater for Workflow passthrough scenario" in {
      assertValid("valid/ValidViewWithEmptyTableUpdaterWorkflow.java")
    }

    "reject View that is not public" in {
      assertInvalid(
        "invalid/NotPublicView.java",
        "NotPublicView is not marked with `public` modifier. Components must be public")
    }

    "reject View with @Table annotation" in {
      assertInvalid("invalid/ViewWithTableAnnotation.java", "A View itself should not be annotated with @Table")
    }

    "reject View with no TableUpdater" in {
      assertInvalid(
        "invalid/ViewWithNoTableUpdater.java",
        "A view must contain at least one public static TableUpdater subclass")
    }

    "reject View with no query method" in {
      assertInvalid("invalid/ViewWithNoQuery.java", "Views should have at least one method annotated with @Query")
    }

    "reject View with invalid row type" in {
      assertInvalid("invalid/ViewWithInvalidRowType.java", "View row type java.lang.String is not supported")
    }

    "reject View with invalid query result type" in {
      assertInvalid(
        "invalid/ViewWrongQueryEffectReturnType.java",
        "View query result type java.lang.String is not supported")
    }

    "reject View with query having too many arguments" in {
      assertInvalid(
        "invalid/ViewQueryWithTooManyArgs.java",
        "must have zero or one argument. If you need to pass more arguments, wrap them in a class")
    }

    "reject View with query not returning QueryEffect" in {
      assertInvalid("invalid/ViewWrongQueryReturnType.java", "Query methods must return View.QueryEffect<RowType>")
    }

    "reject View with update handler having too many parameters" in {
      assertInvalid(
        "invalid/ViewWrongHandlerSignature.java",
        "Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler")
    }

    "reject View TableUpdater without subscription annotation" in {
      assertInvalid(
        "invalid/ViewWithoutSubscription.java",
        "A TableUpdater subclass must be annotated with `@Consume` annotation")
    }

    "reject View with duplicated delete handlers" in {
      assertInvalid(
        "invalid/ViewDuplicatedDeleteHandlers.java",
        "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject View with delete handler having parameters" in {
      assertInvalid(
        "invalid/ViewDeleteHandlerWithParam.java",
        "Method annotated with '@DeleteHandler' must not have parameters")
    }

    "reject View with incorrect stream query return type" in {
      assertInvalid(
        "invalid/ViewWithIncorrectStreamQuery.java",
        "Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>")
    }

    "reject multi-table View without @Table annotations" in {
      assertInvalid(
        "invalid/MultiTableViewWithoutTableAnnotations.java",
        "When there are multiple table updater, each must be annotated with @Table")
    }

    "reject multi-table View with empty @Table name" in {
      assertInvalid("invalid/MultiTableViewWithEmptyTableName.java", "@Table name is empty, must be a non-empty string")
    }

    "reject multi-table View without query method" in {
      assertInvalid(
        "invalid/MultiTableViewWithoutQuery.java",
        "Views should have at least one method annotated with @Query")
    }

    "reject View with ACL on subscription method" in {
      assertInvalid(
        "invalid/ViewWithSubscriptionMethodAcl.java",
        "Methods from classes annotated with Akka @Consume annotations are for internal use only and cannot be annotated with ACL annotations")
    }

    "reject View missing handlers for method level subscription" in {
      assertInvalid(
        "invalid/ViewMissingEventHandler.java",
        "missing an event handler for 'com.example.SimpleEventSourcedEntity.DecrementCounter'")
    }

    "reject View missing handlers for type level subscription" in {
      assertInvalid(
        "invalid/ViewMissingEventHandlerTypeLevel.java",
        "missing an event handler for 'com.example.SimpleEventSourcedEntity.DecrementCounter'")
    }

    "reject View TableUpdater with ambiguous handlers within same table" in {
      assertInvalid(
        "invalid/ViewTableUpdaterWithAmbiguousHandlers.java",
        "Ambiguous handlers for com.example.SimpleEventSourcedEntity.IncrementCounter",
        "methods: [onIncrement1, onIncrement2] consume the same type")
    }

    "reject duplicated VE subscriptions methods in multi table view" in {
      assertInvalid(
        "invalid/MultiTableViewDuplicatedKVEHandlers.java",
        "Ambiguous handlers for java.lang.Integer, methods: [onEvent, onEvent2] consume the same type.")
    }

    "reject duplicated ES subscriptions methods in multi table view" in {
      assertInvalid(
        "invalid/MultiTableViewDuplicatedESHandlers.java",
        "Ambiguous handlers for com.example.SimpleEventSourcedEntity.IncrementCounter, methods: [onEvent, onEvent2] consume the same type.")
    }

    "accept View with KeyValueEntity subscription and transformation handler" in {
      assertValid("valid/ViewWithKVETransformation.java")
    }

    "accept View with Workflow subscription and transformation handler" in {
      assertValid("valid/ViewWithWorkflowTransformation.java")
    }

    "reject View missing KeyValueEntity transformation handler" in {
      assertInvalid(
        "invalid/ViewMissingKVETransformationHandler.java",
        "You are using a type level annotation in this TableUpdater and that requires the TableUpdater type",
        "to match the type",
        "If your intention is to transform the type, you should add a method like",
        "Effect<com.example.ViewMissingKVETransformationHandler.ViewRow>")
    }

    "reject View missing Workflow transformation handler" in {
      assertInvalid(
        "invalid/ViewMissingWorkflowTransformationHandler.java",
        "You are using a type level annotation in this TableUpdater and that requires the TableUpdater type",
        "to match the type",
        "If your intention is to transform the type, you should add a method like",
        "Effect<com.example.ViewMissingWorkflowTransformationHandler.ViewRow>")
    }

    "accept View with @FunctionTool on QueryEffect" in {
      assertValid("valid/ValidViewWithFunctionTool.java")
    }

    "reject View with @FunctionTool on QueryStreamEffect" in {
      assertInvalid(
        "invalid/ViewWithFunctionToolOnQueryStreamEffect.java",
        "View methods annotated with @FunctionTool cannot return QueryStreamEffect.")
    }

    "reject View with @FunctionTool on private methods" in {
      assertInvalid(
        "invalid/ViewWithFunctionToolOnPrivateMethod.java",
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }

    // SnapshotHandler validations
    "accept valid View TableUpdater with @SnapshotHandler for EventSourcedEntity subscription" in {
      assertValid("valid/ValidViewWithSnapshotHandler.java")
    }

    "reject View TableUpdater with @SnapshotHandler on KeyValueEntity subscription" in {
      assertInvalid(
        "invalid/ViewSnapshotHandlerWithKVE.java",
        "@SnapshotHandler can only be used in classes annotated with @Consume.FromEventSourcedEntity")
    }

    "reject View TableUpdater with @SnapshotHandler on ServiceStream subscription with helpful message" in {
      assertInvalid(
        "invalid/ViewSnapshotHandlerWithServiceStream.java",
        "@SnapshotHandler cannot be used with @Consume.FromServiceStream",
        "define the @SnapshotHandler on the producer side")
    }

    "reject View TableUpdater with multiple @SnapshotHandler methods" in {
      assertInvalid(
        "invalid/ViewWithMultipleSnapshotHandlers.java",
        "Only one method can be annotated with @SnapshotHandler")
    }
  }
}
