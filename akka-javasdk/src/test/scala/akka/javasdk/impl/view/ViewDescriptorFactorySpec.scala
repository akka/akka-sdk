/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import akka.javasdk.impl.ComponentDescriptorSuite
import akka.javasdk.impl.InvalidComponentException
import akka.javasdk.impl.Validations
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.EventStreamSubscriptionView
import akka.javasdk.testmodels.subscriptions.PubSubTestModels.SubscribeOnTypeToEventSourcedEvents
import akka.javasdk.testmodels.view.ViewTestModels
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewValidation
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewWithDuplicatedESSubscriptions
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewWithDuplicatedVESubscriptions
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewWithJoinQuery
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewWithMultipleQueries
import akka.javasdk.testmodels.view.ViewTestModels.MultiTableViewWithoutQuery
import akka.javasdk.testmodels.view.ViewTestModels.SubscribeToEventSourcedEvents
import akka.javasdk.testmodels.view.ViewTestModels.SubscribeToEventSourcedWithMissingHandler
import akka.javasdk.testmodels.view.ViewTestModels.SubscribeToSealedEventSourcedEvents
import akka.javasdk.testmodels.view.ViewTestModels.TimeTrackerView
import akka.javasdk.testmodels.view.ViewTestModels.TopicSubscriptionView
import akka.javasdk.testmodels.view.ViewTestModels.TopicTypeLevelSubscriptionView
import akka.javasdk.testmodels.view.ViewTestModels.TransformedUserView
import akka.javasdk.testmodels.view.ViewTestModels.TransformedUserViewWithDeletes
import akka.javasdk.testmodels.view.ViewTestModels.TransformedUserViewWithMethodLevelJWT
import akka.javasdk.testmodels.view.ViewTestModels.TypeLevelSubscribeToEventSourcedEventsWithMissingHandler
import akka.javasdk.testmodels.view.ViewTestModels.UserByEmailWithCollectionReturn
import akka.javasdk.testmodels.view.ViewTestModels.UserByEmailWithStreamReturn
import akka.javasdk.testmodels.view.ViewTestModels.UserViewWithOnlyDeleteHandler
import akka.javasdk.testmodels.view.ViewTestModels.ViewDuplicatedHandleDeletesAnnotations
import akka.javasdk.testmodels.view.ViewTestModels.ViewHandleDeletesWithParam
import akka.javasdk.testmodels.view.ViewTestModels.ViewQueryWithTooManyArguments
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithEmptyComponentIdAnnotation
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithMethodLevelAcl
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithNoQuery
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithServiceLevelAcl
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithServiceLevelJWT
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithTwoQueries
import akka.javasdk.testmodels.view.ViewTestModels.ViewWithoutSubscription
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.timestamp.Timestamp
import com.google.protobuf.{ Any => JavaPbAny }
import kalix.JwtMethodOptions.JwtMethodMode
import kalix.JwtServiceOptions.JwtServiceMode
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ViewDescriptorFactorySpec extends AnyWordSpec with ComponentDescriptorSuite {

  "View descriptor factory" should {

    "validate a View must be declared as public" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[NotPublicView]).failIfInvalid
      }.getMessage should include("NotPublicView is not marked with `public` modifier. Components must be public.")
    }

    "not allow View without any Table updater" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewTestModels.ViewWithNoTableUpdater]).failIfInvalid
      }.getMessage should include("A view must contain at least one public static TableUpdater subclass.")
    }

    "not allow View with Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewTestModels.ViewWithTableName]).failIfInvalid
      }.getMessage should include("A View itself should not be annotated with @Table.")
    }

    "not allow View queries not returning QueryEffect<T>" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewTestModels.WrongQueryReturnType]).failIfInvalid
      }.getMessage should include("Query methods must return View.QueryEffect<RowType>")
    }

    "not allow View update handler with more than on parameter" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewTestModels.WrongHandlerSignature]).failIfInvalid
      }.getMessage should include(
        "Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler.")
    }

    "generate ACL annotations at service level" in {
      assertDescriptor[ViewWithServiceLevelAcl] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate ACL annotations at method level" in {
      assertDescriptor[ViewWithMethodLevelAcl] { desc =>
        val extension = findKalixMethodOptions(desc, "GetEmployeeByEmail")
        val service = extension.getAcl.getAllow(0).getService
        service shouldBe "test"
      }
    }

    "generate query with collection return type" in {
      assertDescriptor[UserByEmailWithCollectionReturn] { desc =>
        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * AS users FROM users WHERE name = :name"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "UserCollection"

        val streamUpdates = queryMethodOptions.getView.getQuery.getStreamUpdates
        streamUpdates shouldBe false
      }
    }

    "generate query with stream return type" in {
      assertDescriptor[UserByEmailWithStreamReturn] { desc =>
        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetAllUsers")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * AS users FROM users"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "User"
        val method = findMethodByName(desc, "GetAllUsers")
        method.isClientStreaming shouldBe false
        method.isServerStreaming shouldBe true

        val streamUpdates = queryMethodOptions.getView.getQuery.getStreamUpdates
        streamUpdates shouldBe false
      }
    }

    "match names out of various queries" in {
      Seq(
        "SELECT * FROM users" -> "users",
        // quoted is also valid
        "SELECT * FROM `users`" -> "users",
        """SELECT * AS customers
          |  FROM customers_by_name
          |  WHERE name = :name
          |""".stripMargin -> "customers_by_name",
        """SELECT * AS customers, next_page_token() AS next_page_token
          |FROM customers
          |OFFSET page_token_offset(:page_token)
          |LIMIT 10""".stripMargin -> "customers").foreach { case (query, expectedTableName) =>
        ViewDescriptorFactory.TableNamePattern.findFirstMatchIn(query).map(_.group(1)) match {
          case Some(tableName) => tableName shouldBe expectedTableName
          case None            => fail(s"pattern does not match [$query]")
        }
      }
    }

  }

  "View descriptor factory (for Key Value Entity)" should {

    "not allow View with empty ComponentId" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithEmptyComponentIdAnnotation]).failIfInvalid
      }.getMessage should include("@ComponentId name is empty, must be a non-empty string.")
    }

    "not allow View with a query with more than 1 param" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewQueryWithTooManyArguments]).failIfInvalid
      }.getMessage should include(
        "Method [getUser] must have zero or one argument. If you need to pass more arguments, wrap them in a class.")
    }

    "not allow method level handle deletes without class level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithoutSubscription]).failIfInvalid
      }.getMessage should include("A TableUpdater subclass must be annotated with `@Consume` annotation.")
    }

    "not allow duplicated handle deletes methods" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewDuplicatedHandleDeletesAnnotations]).failIfInvalid
      }.getMessage should include("Multiple methods annotated with @DeleteHandler are not allowed.")
    }

    "not allow handle deletes method with param" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewHandleDeletesWithParam]).failIfInvalid
      }.getMessage should include("Method annotated with '@DeleteHandler' must not have parameters.")
    }

    "generate proto for a View with explicit update method" in {
      assertDescriptor[TransformedUserView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        val handleDeletes = methodOptions.getEventing.getIn.getHandleDeletes
        entityType shouldBe "user"
        handleDeletes shouldBe false

        methodOptions.getView.getUpdate.getTable shouldBe "users"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions1 = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions1.getView.getQuery.getQuery shouldBe "SELECT * FROM users WHERE email = :email"
        queryMethodOptions1.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions1.getView.getJsonSchema.getInput shouldBe "GetUserAkkaJsonQuery"
        queryMethodOptions1.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions2 = this.findKalixMethodOptions(desc, "GetUsersByEmails")
        queryMethodOptions2.getView.getQuery.getQuery shouldBe "SELECT * as users FROM users WHERE email = :emails"
        queryMethodOptions2.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions2.getView.getJsonSchema.getInput shouldBe "GetUsersByEmailsAkkaJsonQuery"
        queryMethodOptions2.getView.getJsonSchema.getOutput shouldBe "TransformedUsers"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getUser"
      }

    }

    "convert Interval fields to proto Timestamp" in {
      assertDescriptor[TimeTrackerView] { desc =>

        val timerStateMsg = desc.fileDescriptor.findMessageTypeByName("TimerState")
        val createdTimeField = timerStateMsg.findFieldByName("createdTime")
        createdTimeField.getMessageType shouldBe Timestamp.javaDescriptor

        val timerEntry = desc.fileDescriptor.findMessageTypeByName("TimerEntry")
        val startedField = timerEntry.findFieldByName("started")
        startedField.getMessageType shouldBe Timestamp.javaDescriptor

        val stoppedField = timerEntry.findFieldByName("stopped")
        stoppedField.getMessageType shouldBe Timestamp.javaDescriptor
      }
    }

    "generate proto for a View with delete handler" in {
      assertDescriptor[TransformedUserViewWithDeletes] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val in = methodOptions.getEventing.getIn
        in.getValueEntity shouldBe "user"
        in.getHandleDeletes shouldBe false
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true

        val deleteMethodOptions = this.findKalixMethodOptions(desc, "OnDelete")
        val deleteIn = deleteMethodOptions.getEventing.getIn
        deleteIn.getValueEntity shouldBe "user"
        deleteIn.getHandleDeletes shouldBe true
        deleteMethodOptions.getView.getUpdate.getTransformUpdates shouldBe true
      }
    }

    "generate proto for a View with only delete handler" in {
      assertDescriptor[UserViewWithOnlyDeleteHandler] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val in = methodOptions.getEventing.getIn
        in.getValueEntity shouldBe "user"
        in.getHandleDeletes shouldBe false
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe false

        val deleteMethodOptions = this.findKalixMethodOptions(desc, "OnDelete")
        val deleteIn = deleteMethodOptions.getEventing.getIn
        deleteIn.getValueEntity shouldBe "user"
        deleteIn.getHandleDeletes shouldBe true
        deleteMethodOptions.getView.getUpdate.getTransformUpdates shouldBe false
      }
    }

    "generate proto for a View with explicit update method and method level JWT annotation" in {
      assertDescriptor[TransformedUserViewWithMethodLevelJWT] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "OnChange")
        val entityType = methodOptions.getEventing.getIn.getValueEntity
        entityType shouldBe "user"

        methodOptions.getView.getUpdate.getTable shouldBe "users"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetUser")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM users WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe "json_body"
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "TransformedUser"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("TransformedUser")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetUser")
        rule.getPost shouldBe "/akka/v1.0/view/users_view/getUser"

        val method = desc.commandHandlers("GetUser")
        val jwtOption = findKalixMethodOptions(desc, method.grpcMethodName).getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate(0) shouldBe JwtMethodMode.BEARER_TOKEN
        assertRequestFieldJavaType(method, "json_body", JavaType.MESSAGE)

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "generate proto for a View with service level JWT annotation" in {
      assertDescriptor[ViewWithServiceLevelJWT] { desc =>
        val extension = desc.serviceDescriptor.getOptions.getExtension(kalix.Annotations.service)
        val jwtOption = extension.getJwt
        jwtOption.getBearerTokenIssuer(0) shouldBe "a"
        jwtOption.getBearerTokenIssuer(1) shouldBe "b"
        jwtOption.getValidate shouldBe JwtServiceMode.BEARER_TOKEN

        val Seq(claim1, claim2) = jwtOption.getStaticClaimList.asScala.toSeq
        claim1.getClaim shouldBe "role"
        claim1.getValue(0) shouldBe "admin"
        claim2.getClaim shouldBe "aud"
        claim2.getValue(0) shouldBe "${ENV}.kalix.io"
      }
    }

    "fail if no query method found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithNoQuery]).failIfInvalid
      }
    }

    "allow more than one query method" in {
      Validations.validate(classOf[ViewWithTwoQueries]).failIfInvalid
    }

    "not allow stream updates that are not returning View.QueryStreamEffect<T>" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewTestModels.ViewWithIncorrectQueries]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$ViewWithIncorrectQueries#getUserByEmail': Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>"
    }
  }

  "View descriptor factory (for Event Sourced Entity)" should {

    "generate proto for a View" in {
      assertDescriptor[SubscribeToEventSourcedEvents] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")

        methodOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"
        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/akka/v1.0/view/employees_view/getEmployeeByEmail"
      }
    }

    "generate proto for a View when subscribing to sealed interface" in {
      assertDescriptor[SubscribeToSealedEventSourcedEvents] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")

        methodOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"
        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val queryMethodOptions = this.findKalixMethodOptions(desc, "GetEmployeeByEmail")
        queryMethodOptions.getView.getQuery.getQuery shouldBe "SELECT * FROM employees WHERE email = :email"
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        // not defined when query body not used
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val tableMessageDescriptor = desc.fileDescriptor.findMessageTypeByName("Employee")
        tableMessageDescriptor should not be null

        val rule = findHttpRule(desc, "GetEmployeeByEmail")
        rule.getPost shouldBe "/akka/v1.0/view/employees_view/getEmployeeByEmail"

        val onUpdateMethod = desc.commandHandlers("KalixSyntheticMethodOnESEmployee")
        onUpdateMethod.requestMessageDescriptor.getFullName shouldBe JavaPbAny.getDescriptor.getFullName

        val eventing = findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee").getEventing.getIn
        eventing.getEventSourcedEntity shouldBe "employee"

        onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
        contain only ("json.kalix.io/created" -> "handle", "json.kalix.io/old-created" -> "handle", "json.kalix.io/emailUpdated" -> "handle")
      }
    }

    "generate proto for a View with multiple methods to handle different events" in {
      assertDescriptor[SubscribeOnTypeToEventSourcedEvents] { desc =>
        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        val eveningIn = methodOptions.getEventing.getIn
        eveningIn.getEventSourcedEntity shouldBe "employee"
        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
        methodOptions.getEventing.getIn.getIgnore shouldBe false // we don't set the property so the proxy won't ignore. Ignore is only internal to the SDK
      }
    }

    "validate missing handlers for method level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[SubscribeToEventSourcedWithMissingHandler]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$SubscribeToEventSourcedWithMissingHandler$Employees': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "validate missing handlers for type level subscription" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[TypeLevelSubscribeToEventSourcedEventsWithMissingHandler]).failIfInvalid
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$TypeLevelSubscribeToEventSourcedEventsWithMissingHandler$Employees': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }
  }

  "View descriptor factory (for multi-table views)" should {

    "not allow multiple TableUpdater without Table annotation" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewValidation]).failIfInvalid
      }.getMessage should include("When there are multiple table updater, each must be annotated with @Table.")
    }

    "not allow TableUpdater with empty Table name" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewValidation]).failIfInvalid
      }.getMessage should include("@Table name is empty, must be a non-empty string.")
    }

    "not allow empty component id" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[ViewWithEmptyComponentIdAnnotation]).failIfInvalid
      }.getMessage should include("@ComponentId name is empty, must be a non-empty string.")
    }

    "fail if no query method found" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithoutQuery]).failIfInvalid
      }
    }

    "allow more than one query method in multi table view" in {
      Validations.validate(classOf[MultiTableViewWithMultipleQueries]).failIfInvalid
    }

    "not allow duplicated VE subscriptions methods in multi table view" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedVESubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "not allow duplicated ES subscriptions methods in multi table view" in {
      intercept[InvalidComponentException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedESSubscriptions]).failIfInvalid
      }.getMessage should include(
        "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "generate proto for multi-table view with join query" in {
      assertDescriptor[MultiTableViewWithJoinQuery] { desc =>
        val queryMethodOptions = findKalixMethodOptions(desc, "Get")
        queryMethodOptions.getView.getQuery.getQuery should be("""|SELECT employees.*, counters.* as counters
            |FROM employees
            |JOIN assigned ON assigned.assigneeId = employees.email
            |JOIN counters ON assigned.counterId = counters.id
            |WHERE employees.email = :email
            |""".stripMargin)
        queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "EmployeeCounters"
        // not defined when query body not used
//        queryMethodOptions.getView.getJsonSchema.getJsonBodyInputField shouldBe ""
        queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"

        val queryHttpRule = findHttpRule(desc, "Get")
        queryHttpRule.getPost shouldBe "/akka/v1.0/view/multi-table-view-with-join-query/get"

        val employeeCountersMessage = desc.fileDescriptor.findMessageTypeByName("EmployeeCounters")
        employeeCountersMessage should not be null
        val firstNameField = employeeCountersMessage.findFieldByName("firstName")
        firstNameField should not be null
        firstNameField.getType shouldBe FieldDescriptor.Type.STRING
        val lastNameField = employeeCountersMessage.findFieldByName("lastName")
        lastNameField should not be null
        lastNameField.getType shouldBe FieldDescriptor.Type.STRING
        val emailField = employeeCountersMessage.findFieldByName("email")
        emailField should not be null
        emailField.getType shouldBe FieldDescriptor.Type.STRING
        val countersField = employeeCountersMessage.findFieldByName("counters")
        countersField should not be null
        countersField.getMessageType.getName shouldBe "CounterState"
        countersField.isRepeated shouldBe true

        val employeeOnEventOptions = findKalixMethodOptions(desc, "KalixSyntheticMethodOnESEmployee")
        employeeOnEventOptions.getEventing.getIn.getEventSourcedEntity shouldBe "employee"
        employeeOnEventOptions.getView.getUpdate.getTable shouldBe "employees"
        employeeOnEventOptions.getView.getUpdate.getTransformUpdates shouldBe true
        employeeOnEventOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

        val employeeMessage = desc.fileDescriptor.findMessageTypeByName("Employee")
        employeeMessage should not be null
        val employeeFirstNameField = employeeMessage.findFieldByName("firstName")
        employeeFirstNameField should not be null
        employeeFirstNameField.getType shouldBe FieldDescriptor.Type.STRING
        val employeeLastNameField = employeeMessage.findFieldByName("lastName")
        employeeLastNameField should not be null
        employeeLastNameField.getType shouldBe FieldDescriptor.Type.STRING
        val employeeEmailField = employeeMessage.findFieldByName("email")
        employeeEmailField should not be null
        employeeEmailField.getType shouldBe FieldDescriptor.Type.STRING

        val counterOnChangeOptions = findKalixMethodOptions(desc, "OnChange1")
        counterOnChangeOptions.getEventing.getIn.getValueEntity shouldBe "ve-counter"
        counterOnChangeOptions.getView.getUpdate.getTable shouldBe "counters"
        counterOnChangeOptions.getView.getUpdate.getTransformUpdates shouldBe false
        counterOnChangeOptions.getView.getJsonSchema.getOutput shouldBe "CounterState"

        val counterStateMessage = desc.fileDescriptor.findMessageTypeByName("CounterState")
        counterStateMessage should not be null
        val counterStateIdField = counterStateMessage.findFieldByName("id")
        counterStateIdField should not be null
        counterStateIdField.getType shouldBe FieldDescriptor.Type.STRING
        val counterStateValueField = counterStateMessage.findFieldByName("value")
        counterStateValueField should not be null
        counterStateValueField.getType shouldBe FieldDescriptor.Type.INT32

        val assignedCounterOnChangeOptions = findKalixMethodOptions(desc, "OnChange")
        assignedCounterOnChangeOptions.getEventing.getIn.getValueEntity shouldBe "assigned-counter"
        assignedCounterOnChangeOptions.getView.getUpdate.getTable shouldBe "assigned"
        assignedCounterOnChangeOptions.getView.getUpdate.getTransformUpdates shouldBe false
        assignedCounterOnChangeOptions.getView.getJsonSchema.getOutput shouldBe "AssignedCounterState"

        val assignedCounterStateMessage = desc.fileDescriptor.findMessageTypeByName("AssignedCounterState")
        assignedCounterStateMessage should not be null
        val counterIdField = assignedCounterStateMessage.findFieldByName("counterId")
        counterIdField should not be null
        counterIdField.getType shouldBe FieldDescriptor.Type.STRING
        val assigneeIdField = assignedCounterStateMessage.findFieldByName("assigneeId")
        assigneeIdField should not be null
        assigneeIdField.getType shouldBe FieldDescriptor.Type.STRING
      }
    }
  }

  "View descriptor factory (for Stream)" should {
    "generate mappings for service to service subscription " in {
      assertDescriptor[EventStreamSubscriptionView] { desc =>

        val serviceOptions = findKalixServiceOptions(desc)
        val eventingInDirect = serviceOptions.getEventing.getIn.getDirect
        eventingInDirect.getService shouldBe "employee_service"
        eventingInDirect.getEventStreamId shouldBe "employee_events"

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnStreamEmployeeevents")

        methodOptions.hasEventing shouldBe false
        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"

      }
    }
  }

  "View descriptor factory (for Topic)" should {
    "generate mappings for topic type level subscription " in {
      assertDescriptor[TopicTypeLevelSubscriptionView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource")

        val eventingInTopic = methodOptions.getEventing.getIn
        eventingInTopic.getTopic shouldBe "source"
        eventingInTopic.getConsumerGroup shouldBe "cg"

        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      }
    }

    "generate mappings for topic subscription " in {
      assertDescriptor[TopicSubscriptionView] { desc =>

        val methodOptions = this.findKalixMethodOptions(desc, "KalixSyntheticMethodOnTopicSource")

        val eventingInTopic = methodOptions.getEventing.getIn
        eventingInTopic.getTopic shouldBe "source"
        eventingInTopic.getConsumerGroup shouldBe "cg"

        methodOptions.getView.getUpdate.getTable shouldBe "employees"
        methodOptions.getView.getUpdate.getTransformUpdates shouldBe true
        methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      }
    }
  }
}
