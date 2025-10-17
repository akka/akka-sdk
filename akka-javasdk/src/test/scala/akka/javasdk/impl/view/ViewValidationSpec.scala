/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.impl.Validations
import akka.javasdk.impl.ValidationSupportSpec
import akka.javasdk.testmodels.view.ViewTestModels
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ViewValidationSpec extends AnyWordSpec with Matchers with ValidationSupportSpec {

  import ViewTestModels._

  "View validation" should {

    "return Invalid for View not declared as public" in {
      Validations
        .validate(classOf[NotPublicView])
        .expectInvalid("NotPublicView is not marked with `public` modifier. Components must be public.")
    }

    "return Invalid for View without any Table updater" in {
      Validations
        .validate(classOf[ViewWithNoTableUpdater])
        .expectInvalid("A view must contain at least one public static TableUpdater subclass.")
    }

    "return Invalid for View with an invalid row type" in {
      Validations
        .validate(classOf[ViewWithInvalidRowType])
        .expectInvalid("View row type java.lang.String is not supported")
    }

    "return Invalid for View with an invalid query result type" in {
      Validations
        .validate(classOf[WrongQueryEffectReturnType])
        .expectInvalid("View query result type java.lang.String is not supported")
    }

    "return Invalid for View with Table annotation" in {
      Validations
        .validate(classOf[ViewWithTableName])
        .expectInvalid("A View itself should not be annotated with @Table.")
    }

    "return Invalid for View queries not returning QueryEffect<T>" in {
      Validations
        .validate(classOf[WrongQueryReturnType])
        .expectInvalid("Query methods must return View.QueryEffect<RowType>")
    }

    "return Invalid for View update handler with more than one parameter" in {
      Validations
        .validate(classOf[WrongHandlerSignature])
        .expectInvalid("Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler.")
    }

    "return Invalid for View annotated with @Table" in {
      Validations
        .validate(classOf[ViewWithoutComponentAnnotation])
        .expectInvalid("A View itself should not be annotated with @Table.")
    }

    "return Invalid for View with empty Component" in {
      Validations
        .validate(classOf[ViewWithEmptyComponentAnnotation])
        .expectInvalid("@Component id is empty, must be a non-empty string.")
    }

    "return Invalid for View with a query with more than 1 param" in {
      Validations
        .validate(classOf[ViewQueryWithTooManyArguments])
        .expectInvalid(
          "Method [getUser] must have zero or one argument. If you need to pass more arguments, wrap them in a class.")
    }

    "return Invalid for method level handle deletes without class level subscription" in {
      Validations
        .validate(classOf[ViewWithoutSubscription])
        .expectInvalid("A TableUpdater subclass must be annotated with `@Consume` annotation.")
    }

    "return Invalid for duplicated handle deletes methods" in {
      Validations
        .validate(classOf[ViewDuplicatedHandleDeletesAnnotations])
        .expectInvalid("Multiple methods annotated with @DeleteHandler are not allowed.")
    }

    "return Invalid for handle deletes method with param" in {
      Validations
        .validate(classOf[ViewHandleDeletesWithParam])
        .expectInvalid("Method annotated with '@DeleteHandler' must not have parameters.")
    }

    "return Invalid for View with no query method" in {
      Validations
        .validate(classOf[ViewWithNoQuery])
        .expectInvalid("A view must contain at least one public static TableUpdater subclass")
    }

    "return Invalid for stream updates not returning View.QueryStreamEffect<T>" in {
      Validations
        .validate(classOf[ViewTestModels.ViewWithIncorrectQueries])
        .expectInvalid("Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>")
    }

    "return Invalid for View missing handlers for method level subscription" in {
      Validations
        .validate(classOf[SubscribeToEventSourcedWithMissingHandler])
        .expectInvalid(
          "missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'")
    }

    "return Invalid for View missing handlers for type level subscription" in {
      Validations
        .validate(classOf[TypeLevelSubscribeToEventSourcedEventsWithMissingHandler])
        .expectInvalid(
          "missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'")
    }

    "return Invalid for multiple TableUpdater without Table annotation" in {
      Validations
        .validate(classOf[MultiTableViewValidation])
        .expectInvalid("When there are multiple table updater, each must be annotated with @Table.")
    }

    "return Invalid for TableUpdater with empty Table name" in {
      Validations
        .validate(classOf[MultiTableViewValidation])
        .expectInvalid("@Table name is empty, must be a non-empty string.")
    }

    "return Invalid for invalid component id" in {
      Validations
        .validate(classOf[ViewWithPipeyComponentAnnotation])
        .expectInvalid("must not contain the pipe character")
    }

    "return Invalid for multi table view without query method" in {
      Validations
        .validate(classOf[MultiTableViewWithoutQuery])
        .expectInvalid("No valid query method found. Views should have at least one method annotated with @Query.")
    }

    "return Invalid for duplicated VE subscriptions methods in multi table view" in {
      Validations
        .validate(classOf[MultiTableViewWithDuplicatedVESubscriptions])
        .expectInvalid(
          "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "return Invalid for duplicated ES subscriptions methods in multi table view" in {
      Validations
        .validate(classOf[MultiTableViewWithDuplicatedESSubscriptions])
        .expectInvalid(
          "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "return Invalid for View TableUpdater with ACL on subscription method" in {
      Validations
        .validate(classOf[ViewWithSubscriptionMethodAcl])
        .expectInvalid(
          "Methods from classes annotated with Akka @Consume annotations are for internal use only and cannot be annotated with ACL annotations.")
    }

    "allow more than one query method in multi table view" in {
      Validations.validate(classOf[MultiTableViewWithMultipleQueries]).isValid shouldBe true
    }

    "allow more than one query method" in {
      Validations.validate(classOf[ViewWithTwoQueries]).isValid shouldBe true
    }

  }
}
