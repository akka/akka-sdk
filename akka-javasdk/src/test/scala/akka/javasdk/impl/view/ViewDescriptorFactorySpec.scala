/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import scala.reflect.ClassTag

import akka.dispatch.ExecutionContexts
import akka.javasdk.impl.ValidationException
import akka.javasdk.impl.Validations
import akka.javasdk.impl.serialization.JsonSerializer
import akka.javasdk.testmodels.view.ViewTestModels
import akka.runtime.sdk.spi.ConsumerSource
import akka.runtime.sdk.spi.Principal
import akka.runtime.sdk.spi.RegionInfo
import akka.runtime.sdk.spi.ServiceNamePattern
import akka.runtime.sdk.spi.SpiSchema.SpiClass
import akka.runtime.sdk.spi.SpiSchema.SpiInteger
import akka.runtime.sdk.spi.SpiSchema.SpiList
import akka.runtime.sdk.spi.SpiSchema.SpiString
import akka.runtime.sdk.spi.SpiSchema.SpiTimestamp
import akka.runtime.sdk.spi.ViewDescriptor
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ViewDescriptorFactorySpec extends AnyWordSpec with Matchers {

  import ViewTestModels._
  import akka.javasdk.testmodels.subscriptions.PubSubTestModels._

  def assertDescriptor[T](test: ViewDescriptor => Any)(implicit tag: ClassTag[T]): Unit = {
    Validations.validate(tag.runtimeClass).failIfInvalid()
    test(ViewDescriptorFactory(tag.runtimeClass, new JsonSerializer, new RegionInfo(""), ExecutionContexts.global()))
  }

  "View descriptor factory" should {

    "validate a View must be declared as public" in {
      intercept[ValidationException] {
        Validations.validate(classOf[NotPublicView]).failIfInvalid()
      }.getMessage should include("NotPublicView is not marked with `public` modifier. Components must be public.")
    }

    "allow View with lower case query" in {
      assertDescriptor[ViewWithLowerCaseQuery] { desc =>
        desc.tables.map(_.tableName) shouldBe Seq("users")
      }
    }

    "allow View query with quoted table name" in {
      assertDescriptor[ViewWithQuotedTableName] { desc =>
        desc.tables.map(_.tableName) shouldBe Seq("üsérs tåble")
      }
    }

    "not allow View without any Table updater" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithNoTableUpdater]).failIfInvalid()
      }.getMessage should include("A view must contain at least one public static TableUpdater subclass.")
    }

    "not allow View with an invalid row type" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithInvalidRowType]).failIfInvalid()
      }.getMessage should include(s"View row type java.lang.String is not supported")
    }

    "not allow View with an invalid query result type" in {
      intercept[ValidationException] {
        Validations.validate(classOf[WrongQueryEffectReturnType]).failIfInvalid()
      }.getMessage should include("View query result type java.lang.String is not supported")
    }

    "not allow View with Table annotation" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithTableName]).failIfInvalid()
      }.getMessage should include("A View itself should not be annotated with @Table.")
    }

    "not allow View queries not returning QueryEffect<T>" in {
      intercept[ValidationException] {
        Validations.validate(classOf[WrongQueryReturnType]).failIfInvalid()
      }.getMessage should include("Query methods must return View.QueryEffect<RowType>")
    }

    "not allow View update handler with more than on parameter" in {
      intercept[ValidationException] {
        Validations.validate(classOf[WrongHandlerSignature]).failIfInvalid()
      }.getMessage should include(
        "Subscription method must have exactly one parameter, unless it's marked with @DeleteHandler.")
    }

    "generate ACL annotations at service level" in pendingUntilFixed {
      assertDescriptor[ViewWithServiceLevelAcl] { desc =>
        val options = desc.componentOptions
        val acl = options.aclOpt.get
        acl.allow.head match {
          case _: Principal => fail()
          case pattern: ServiceNamePattern =>
            pattern.pattern shouldBe "test"
        }
      }
    }

    "generate ACL annotations at method level" in pendingUntilFixed {
      assertDescriptor[ViewWithMethodLevelAcl] { desc =>
        val query = desc.queries.find(_.name == "getEmployeeByEmail").get
        val acl = query.methodOptions.acl.get
        acl.allow.head match {
          case _: Principal => fail()
          case pattern: ServiceNamePattern =>
            pattern.pattern shouldBe "test"
        }
      }
    }

    "generate query with collection return type" in {
      assertDescriptor[UserByEmailWithCollectionReturn] { desc =>
        val query = desc.queries.find(_.name == "getUser").get

        query.query shouldBe "SELECT * AS users FROM users WHERE name = :name"
        query.streamUpdates shouldBe false
      }
    }

    "generate query with stream return type" in {
      assertDescriptor[UserByEmailWithStreamReturn] { desc =>
        val query = desc.queries.find(_.name == "getAllUsers").get
        query.query shouldBe "SELECT * AS users FROM users"
        query.outputType shouldBe an[SpiList]
        query.streamUpdates shouldBe false
      }
    }

    "match table names out of various queries" in {
      Seq(
        "SELECT * FROM users" -> "users",
        // lower case FROM keyword is valid
        "select * from users" -> "users",
        // any case FROM keyword is valid
        "select * fRoM users" -> "users",
        // quoted is also valid
        "SELECT * FROM `users`" -> "users",
        // quoted can contain any characters
        "SELECT * FROM `üsérs tåble`" -> "üsérs tåble",
        """SELECT * AS customers
          |  FROM customers_by_name
          |  WHERE name = :name
          |""".stripMargin -> "customers_by_name",
        """SELECT * AS customers, next_page_token() AS next_page_token
          |FROM customers
          |OFFSET page_token_offset(:page_token)
          |LIMIT 10""".stripMargin -> "customers").foreach { case (query, expectedTableName) =>
        ViewDescriptorFactory.TableNamePattern
          .findFirstMatchIn(query)
          .map(m => Option(m.group(1)).getOrElse(m.group(2))) match {
          case Some(tableName) => tableName shouldBe expectedTableName
          case None            => fail(s"pattern does not match [$query]")
        }
      }
    }

  }

  "View descriptor factory (for Key Value Entity)" should {

    "not allow View without ComponentId annotation" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithoutComponentAnnotation]).failIfInvalid()
      }.getMessage should include("A View itself should not be annotated with @Table.")
    }

    "not allow View with empty Component" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithEmptyComponentAnnotation]).failIfInvalid()
      }.getMessage should include("@Component id is empty, must be a non-empty string.")
    }

    "not allow View with a query with more than 1 param" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewQueryWithTooManyArguments]).failIfInvalid()
      }.getMessage should include(
        "Method [getUser] must have zero or one argument. If you need to pass more arguments, wrap them in a class.")
    }

    "not allow method level handle deletes without class level subscription" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithoutSubscription]).failIfInvalid()
      }.getMessage should include("A TableUpdater subclass must be annotated with `@Consume` annotation.")
    }

    "not allow duplicated handle deletes methods" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewDuplicatedHandleDeletesAnnotations]).failIfInvalid()
      }.getMessage should include("Multiple methods annotated with @DeleteHandler are not allowed.")
    }

    "not allow handle deletes method with param" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewHandleDeletesWithParam]).failIfInvalid()
      }.getMessage should include("Method annotated with '@DeleteHandler' must not have parameters.")
    }

    "convert Interval fields to proto Timestamp" in {
      assertDescriptor[TimeTrackerView] { desc =>
        // FIXME move to schema spec, not about descriptor in general
        val table = desc.tables.find(_.tableName == "time_trackers").get
        val createdTimeField = table.tableType.getField("createdTime").get
        createdTimeField.fieldType shouldBe SpiTimestamp

        val timerEntry =
          table.tableType.getField("entries").get.fieldType.asInstanceOf[SpiList].valueType.asInstanceOf[SpiClass]
        val startedField = timerEntry.getField("started").get
        startedField.fieldType shouldBe SpiTimestamp

        val stoppedField = timerEntry.getField("stopped").get
        stoppedField.fieldType shouldBe SpiTimestamp
      }
    }

    "create a descriptor for a View with a delete handler" in {
      assertDescriptor[TransformedUserViewWithDeletes] { desc =>

        val table = desc.tables.find(_.tableName == "users").get

        table.updateHandler shouldBe defined
        table.deleteHandler shouldBe defined

        table.consumerSource shouldBe a[ConsumerSource.KeyValueEntitySource]
        table.consumerSource.asInstanceOf[ConsumerSource.KeyValueEntitySource].componentId shouldBe "user"
      }
    }

    "create a descriptor for a View with only delete handler" in {
      assertDescriptor[UserViewWithOnlyDeleteHandler] { desc =>
        val table = desc.tables.find(_.tableName == "users").get

        table.updateHandler shouldBe empty
        table.deleteHandler shouldBe defined
      }
    }

    "create a descriptor for a View without transformation" in {
      assertDescriptor[UserViewWithoutTransformation] { desc =>
        val table = desc.tables.find(_.tableName == "users").get

        table.updateHandler shouldBe empty
        table.deleteHandler shouldBe empty
      }
    }

    "fail if no query method found" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithNoQuery]).failIfInvalid()
      }
    }

    "allow more than one query method" in {
      Validations.validate(classOf[ViewWithTwoQueries]).failIfInvalid()
    }

    "not allow stream updates that are not returning View.QueryStreamEffect<T>" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewTestModels.ViewWithIncorrectQueries]).failIfInvalid()
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$ViewWithIncorrectQueries#getUserByEmail': Query methods marked with streamUpdates must return View.QueryStreamEffect<RowType>"
    }

  }

  "View descriptor factory (for Event Sourced Entity)" should {

    "create a descriptor for a View" in {
      assertDescriptor[SubscribeToEventSourcedEvents] { desc =>

        val table = desc.tables.find(_.tableName == "employees").get

        table.consumerSource match {
          case es: ConsumerSource.EventSourcedEntitySource =>
            es.componentId shouldBe "employee"
          case _ => fail()
        }
        table.updateHandler shouldBe defined

        val query = desc.queries.find(_.name == "getEmployeeByEmail").get
        query.query shouldBe "SELECT * FROM employees WHERE email = :email"
      // queryMethodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      // not defined when query body not used
      // queryMethodOptions.getView.getJsonSchema.getInput shouldBe "ByEmail"
      }
    }

    "create a descriptor for a View when subscribing to sealed interface" in {
      assertDescriptor[SubscribeToSealedEventSourcedEvents] { desc =>

        val table = desc.tables.find(_.tableName == "employees").get
        table.consumerSource match {
          case es: ConsumerSource.EventSourcedEntitySource =>
            es.componentId shouldBe "employee"
          case _ => fail()
        }
        table.updateHandler shouldBe defined

        val query = desc.queries.find(_.name == "getEmployeeByEmail").get
        query.query shouldBe "SELECT * FROM employees WHERE email = :email"

        table.consumerSource match {
          case es: ConsumerSource.EventSourcedEntitySource =>
            es.componentId shouldBe "employee"
          case _ => fail()
        }

      // onUpdateMethod.methodInvokers.view.mapValues(_.method.getName).toMap should
      // contain only ("json.akka.io/created" -> "handle", "json.akka.io/old-created" -> "handle", "json.akka.io/emailUpdated" -> "handle")
      }
    }

    "create a descriptor for a View with multiple methods to handle different events" in {
      assertDescriptor[SubscribeOnTypeToEventSourcedEvents] { desc =>
        val table = desc.tables.find(_.tableName == "employees").get

        table.consumerSource match {
          case es: ConsumerSource.EventSourcedEntitySource =>
            es.componentId shouldBe "employee"
          case _ => fail()
        }

        table.updateHandler shouldBe defined
      // methodOptions.getView.getJsonSchema.getOutput shouldBe "Employee"
      // methodOptions.getEventing.getIn.getIgnore shouldBe false // we don't set the property so the runtime won't ignore. Ignore is only internal to the SDK
      }
    }

    "validate missing handlers for method level subscription" in {
      intercept[ValidationException] {
        Validations.validate(classOf[SubscribeToEventSourcedWithMissingHandler]).failIfInvalid()
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$SubscribeToEventSourcedWithMissingHandler$Employees': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }

    "validate missing handlers for type level subscription" in {
      intercept[ValidationException] {
        Validations.validate(classOf[TypeLevelSubscribeToEventSourcedEventsWithMissingHandler]).failIfInvalid()
      }.getMessage shouldBe
      "On 'akka.javasdk.testmodels.view.ViewTestModels$TypeLevelSubscribeToEventSourcedEventsWithMissingHandler$Employees': missing an event handler for 'akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent$EmployeeEmailUpdated'."
    }
  }

  "View descriptor factory (for multi-table views)" should {

    "not allow multiple TableUpdater without Table annotation" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultiTableViewValidation]).failIfInvalid()
      }.getMessage should include("When there are multiple table updater, each must be annotated with @Table.")
    }

    "not allow TableUpdater with empty Table name" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultiTableViewValidation]).failIfInvalid()
      }.getMessage should include("@Table name is empty, must be a non-empty string.")
    }

    "not allow empty component id" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithEmptyComponentAnnotation]).failIfInvalid()
      }.getMessage should include("@Component id is empty, must be a non-empty string.")
    }

    "not allow invalid component id" in {
      intercept[ValidationException] {
        Validations.validate(classOf[ViewWithPipeyComponentAnnotation]).failIfInvalid()
      }.getMessage should include("must not contain the pipe character")
    }

    "fail if no query method found" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultiTableViewWithoutQuery]).failIfInvalid()
      }
    }

    "allow more than one query method in multi table view" in {
      Validations.validate(classOf[MultiTableViewWithMultipleQueries]).failIfInvalid()
    }

    "not allow duplicated VE subscriptions methods in multi table view" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedVESubscriptions]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "not allow duplicated ES subscriptions methods in multi table view" in {
      intercept[ValidationException] {
        Validations.validate(classOf[MultiTableViewWithDuplicatedESSubscriptions]).failIfInvalid()
      }.getMessage should include(
        "Ambiguous handlers for akka.javasdk.testmodels.keyvalueentity.CounterState, methods: [onEvent, onEvent2] consume the same type.")
    }

    "create a descriptor for multi-table view with join query" in {
      assertDescriptor[MultiTableViewWithJoinQuery] { desc =>
        val query = desc.queries.find(_.name == "get").get
        query.query should be("""|SELECT employees.*, counters.* as counters
                |FROM employees
                |JOIN assigned ON assigned.assigneeId = employees.email
                |JOIN counters ON assigned.counterId = counters.id
                |WHERE employees.email = :email
                |""".stripMargin)

        desc.tables should have size 3

        val employeesTable = desc.tables.find(_.tableName == "employees").get
        employeesTable.updateHandler shouldBe defined
        employeesTable.tableType.getField("firstName").get.fieldType shouldBe SpiString
        employeesTable.tableType.getField("lastName").get.fieldType shouldBe SpiString
        employeesTable.tableType.getField("email").get.fieldType shouldBe SpiString

        val countersTable = desc.tables.find(_.tableName == "counters").get
        countersTable.updateHandler shouldBe empty
        countersTable.tableType.getField("id").get.fieldType shouldBe SpiString
        countersTable.tableType.getField("value").get.fieldType shouldBe SpiInteger

        val assignedTable = desc.tables.find(_.tableName == "assigned").get
        assignedTable.updateHandler shouldBe empty
        assignedTable.tableType.getField("counterId").get.fieldType shouldBe SpiString
        assignedTable.tableType.getField("assigneeId").get.fieldType shouldBe SpiString
      }
    }
  }

  "View descriptor factory (for Stream)" should {
    "create a descriptor for service to service subscription " in {
      assertDescriptor[EventStreamSubscriptionView] { desc =>

        val table = desc.tables.find(_.tableName == "employees").get

        table.consumerSource match {
          case stream: ConsumerSource.ServiceStreamSource =>
            stream.service shouldBe "employee_service"
            stream.streamId shouldBe "employee_events"
          case _ => fail()
        }

        table.updateHandler shouldBe defined
      }
    }
  }

  "View descriptor factory (for Topic)" should {
    "create a descriptor for topic type level subscription " in {
      assertDescriptor[TopicTypeLevelSubscriptionView] { desc =>
        val table = desc.tables.find(_.tableName == "employees").get

        table.consumerSource match {
          case topic: ConsumerSource.TopicSource =>
            topic.name shouldBe "source"
            topic.consumerGroup shouldBe "cg"
          case _ => fail()
        }

        table.updateHandler shouldBe defined
      }
    }

    "create a descriptor for a view with a recursive table type" in {
      assertDescriptor[RecursiveViewStateView] { desc =>
        // just check that it parses
      }
    }

    "create a descriptor for a view with a table type with all possible column types" in {
      assertDescriptor[AllTheFieldTypesView] { desc =>
        // just check that it parses
      }
    }

  }
}
