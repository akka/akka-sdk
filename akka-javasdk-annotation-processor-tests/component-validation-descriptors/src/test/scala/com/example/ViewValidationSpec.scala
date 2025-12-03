/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ViewValidationSpec extends AnyWordSpec with Matchers with CompilationTestSupport {

  "View validation" should {

    "accept valid View" in {
      val result = compileTestSource("valid/ValidView.java")
      assertCompilationSuccess(result)
    }

    "accept View with two queries" in {
      val result = compileTestSource("valid/ViewWithTwoQueries.java")
      assertCompilationSuccess(result)
    }

    "accept multi-table View with multiple queries" in {
      val result = compileTestSource("valid/MultiTableViewWithMultipleQueries.java")
      assertCompilationSuccess(result)
    }

    "accept View with empty TableUpdater for KeyValueEntity passthrough scenario" in {
      val result = compileTestSource("valid/ValidViewWithEmptyTableUpdater.java")
      assertCompilationSuccess(result)
    }

    "accept View with empty TableUpdater for Workflow passthrough scenario" in {
      val result = compileTestSource("valid/ValidViewWithEmptyTableUpdaterWorkflow.java")
      assertCompilationSuccess(result)
    }

    "reject View that is not public" in {
      val result = compileTestSource("invalid/NotPublicView.java")
      assertCompilationFailure(result, "NotPublicView is not marked with `public` modifier. Components must be public")
    }

    "reject View with @Table annotation" in {
      val result = compileTestSource("invalid/ViewWithTableAnnotation.java")
      assertCompilationFailure(result, "A View itself should not be annotated with @Table")
    }

    "reject View with no TableUpdater" in {
      val result = compileTestSource("invalid/ViewWithNoTableUpdater.java")
      assertCompilationFailure(result, "A view must contain at least one public static TableUpdater subclass")
    }

    "reject View with no query method" in {
      val result = compileTestSource("invalid/ViewWithNoQuery.java")
      assertCompilationFailure(result, "Views should have at least one method annotated with @Query")
    }

    "reject View with invalid row type" in {
      val result = compileTestSource("invalid/ViewWithInvalidRowType.java")
      assertCompilationFailure(result, "View row type java.lang.String is not supported")
    }

    "reject View with invalid query result type" in {
      val result = compileTestSource("invalid/ViewWrongQueryEffectReturnType.java")
      assertCompilationFailure(result, "View query result type java.lang.String is not supported")
    }

    "reject View with query having too many arguments" in {
      val result = compileTestSource("invalid/ViewQueryWithTooManyArgs.java")
      assertCompilationFailure(
        result,
        "must have zero or one argument. If you need to pass more arguments, wrap them in a class")
    }

    "reject View with query not returning QueryEffect" in {
      val result = compileTestSource("invalid/ViewWrongQueryReturnType.java")
      assertCompilationFailure(result, "Query methods must return View.QueryEffect<RowType>")
    }

    "reject View with update handler having too many parameters" in {
      val result = compileTestSource("invalid/ViewWrongHandlerSignature.java")
      assertCompilationFailure(
        result,
        "Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler")
    }

    "reject View TableUpdater without subscription annotation" in {
      val result = compileTestSource("invalid/ViewWithoutSubscription.java")
      assertCompilationFailure(result, "A TableUpdater subclass must be annotated with `@Consume` annotation")
    }

    "reject View with duplicated delete handlers" in {
      val result = compileTestSource("invalid/ViewDuplicatedDeleteHandlers.java")
      assertCompilationFailure(result, "Multiple methods annotated with @DeleteHandler are not allowed")
    }

    "reject View with delete handler having parameters" in {
      val result = compileTestSource("invalid/ViewDeleteHandlerWithParam.java")
      assertCompilationFailure(result, "Method annotated with '@DeleteHandler' must not have parameters")
    }

    "reject View with incorrect stream query return type" in {
      val result = compileTestSource("invalid/ViewWithIncorrectStreamQuery.java")
      assertCompilationFailure(
        result,
        "Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>")
    }

    "reject multi-table View without @Table annotations" in {
      val result = compileTestSource("invalid/MultiTableViewWithoutTableAnnotations.java")
      assertCompilationFailure(result, "When there are multiple table updater, each must be annotated with @Table")
    }

    "reject multi-table View with empty @Table name" in {
      val result = compileTestSource("invalid/MultiTableViewWithEmptyTableName.java")
      assertCompilationFailure(result, "@Table name is empty, must be a non-empty string")
    }

    "reject multi-table View without query method" in {
      val result = compileTestSource("invalid/MultiTableViewWithoutQuery.java")
      assertCompilationFailure(result, "Views should have at least one method annotated with @Query")
    }

    "reject View with ACL on subscription method" in {
      val result = compileTestSource("invalid/ViewWithSubscriptionMethodAcl.java")
      assertCompilationFailure(
        result,
        "Methods from classes annotated with Akka @Consume annotations are for internal use only and cannot be annotated with ACL annotations")
    }

    "reject View missing handlers for method level subscription" in {
      val result = compileTestSource("invalid/ViewMissingEventHandler.java")
      assertCompilationFailure(
        result,
        "missing an event handler for 'com.example.SimpleEventSourcedEntity.DecrementCounter'")
    }

    "reject View missing handlers for type level subscription" in {
      val result = compileTestSource("invalid/ViewMissingEventHandlerTypeLevel.java")
      assertCompilationFailure(
        result,
        "missing an event handler for 'com.example.SimpleEventSourcedEntity.DecrementCounter'")
    }

    "reject View TableUpdater with ambiguous handlers within same table" in {
      val result = compileTestSource("invalid/ViewTableUpdaterWithAmbiguousHandlers.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for com.example.SimpleEventSourcedEntity.IncrementCounter",
        "methods: [onIncrement1, onIncrement2] consume the same type")
    }

    "reject duplicated VE subscriptions methods in multi table view" in {
      val result = compileTestSource("invalid/MultiTableViewDuplicatedKVEHandlers.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for java.lang.Integer, methods: [onEvent, onEvent2] consume the same type.")
    }

    "reject duplicated ES subscriptions methods in multi table view" in {
      val result = compileTestSource("invalid/MultiTableViewDuplicatedESHandlers.java")
      assertCompilationFailure(
        result,
        "Ambiguous handlers for com.example.SimpleEventSourcedEntity.IncrementCounter, methods: [onEvent, onEvent2] consume the same type.")
    }

    "accept View with KeyValueEntity subscription and transformation handler" in {
      val result = compileTestSource("valid/ViewWithKVETransformation.java")
      assertCompilationSuccess(result)
    }

    "accept View with Workflow subscription and transformation handler" in {
      val result = compileTestSource("valid/ViewWithWorkflowTransformation.java")
      assertCompilationSuccess(result)
    }

    "reject View missing KeyValueEntity transformation handler" in {
      val result = compileTestSource("invalid/ViewMissingKVETransformationHandler.java")
      assertCompilationFailure(
        result,
        "You are using a type level annotation in this TableUpdater and that requires the TableUpdater type",
        "to match the type",
        "If your intention is to transform the type, you should add a method like",
        "Effect<com.example.ViewMissingKVETransformationHandler.ViewRow>")
    }

    "reject View missing Workflow transformation handler" in {
      val result = compileTestSource("invalid/ViewMissingWorkflowTransformationHandler.java")
      assertCompilationFailure(
        result,
        "You are using a type level annotation in this TableUpdater and that requires the TableUpdater type",
        "to match the type",
        "If your intention is to transform the type, you should add a method like",
        "Effect<com.example.ViewMissingWorkflowTransformationHandler.ViewRow>")
    }

    "accept View with @FunctionTool on QueryEffect" in {
      val result = compileTestSource("valid/ValidViewWithFunctionTool.java")
      assertCompilationSuccess(result)
    }

    "reject View with @FunctionTool on QueryStreamEffect" in {
      val result = compileTestSource("invalid/ViewWithFunctionToolOnQueryStreamEffect.java")
      assertCompilationFailure(result, "View methods annotated with @FunctionTool cannot return QueryStreamEffect.")
    }

    "reject View with @FunctionTool on private methods" in {
      val result = compileTestSource("invalid/ViewWithFunctionToolOnPrivateMethod.java")
      assertCompilationFailure(
        result,
        "Methods annotated with @FunctionTool must be public. Method [privateMethod] cannot be annotated with @FunctionTool")
    }
  }
}
