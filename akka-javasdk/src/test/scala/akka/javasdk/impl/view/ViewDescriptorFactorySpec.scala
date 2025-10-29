/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.view

import scala.reflect.ClassTag

import akka.dispatch.ExecutionContexts
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
    Validations.validate(tag.runtimeClass).isValid shouldBe true
    test(ViewDescriptorFactory(tag.runtimeClass, new JsonSerializer, new RegionInfo(""), ExecutionContexts.global()))
  }

  "View descriptor factory" should {

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

  }

  "View descriptor factory (for multi-table views)" should {

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
