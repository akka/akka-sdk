/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.subscriptions;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.Query;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.testmodels.Done;
import akka.javasdk.testmodels.Message;
import akka.javasdk.testmodels.eventsourcedentity.Employee;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent.EmployeeCreated;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent.EmployeeEmailUpdated;
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity;
import akka.javasdk.testmodels.keyvalueentity.Counter;
import akka.javasdk.testmodels.keyvalueentity.CounterState;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import protoconsumer.EventsForConsumer;

public
class PubSubTestModels { // TODO shall we remove this class and move things to ActionTestModels and
  // ViewTestModels

  @Consume.FromKeyValueEntity(Counter.class)
  public static class SubscribeToValueEntityTypeLevel extends Consumer {

    public Effect onUpdate(CounterState message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class SubscribeToValueEntityWithDeletes extends Consumer {

    public Effect onUpdate(CounterState message) {
      return effects().produce(message);
    }

    @DeleteHandler
    public Effect onDelete() {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class SubscribeToEventSourcedEmployee extends Consumer {

    public Effect methodOne(EmployeeCreated message) {
      return effects().produce(message);
    }

    public Effect methodTwo(EmployeeEmailUpdated message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
  public static class SubscribeToTopicTypeLevel extends Consumer {

    public Effect messageOne(Message message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg", ignoreUnknown = true)
  public static class SubscribeToTopicTypeLevelCombined extends Consumer {

    public Effect messageOne(Message message) {
      return effects().produce(message);
    }

    public Effect messageTwo(String message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic("foobar")
  public static class SubscribeToBytesFromTopic extends Consumer {

    public Effect consume(byte[] bytes) {
      return effects().produce(Done.instance);
    }
  }

  // common query parameter for views in this file
  public record ByEmail(String email) {}

  @Component(id = "employee_view")
  public static class SubscribeOnTypeToEventSourcedEvents extends View {

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class, ignoreUnknown = true)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreate(EmployeeCreated evt) {
        return effects().updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects()
            .updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }

  @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
  @Produce.ServiceStream(id = "employee_events")
  public static class EventStreamPublishingConsumer extends Consumer {

    public Effect transform(EmployeeEvent event) {
      return switch (event) {
        case EmployeeCreated created -> effects().produce(created.toString());
        case EmployeeEmailUpdated emailUpdated -> effects().produce(emailUpdated.toString());
      };
    }
  }

  @Consume.FromServiceStream(
      service = "employee_service",
      id = "employee_events",
      ignoreUnknown = true)
  public static class EventStreamSubscriptionConsumer extends Consumer {

    public Effect transform(EmployeeCreated created) {
      return effects().produce(created.toString());
    }

    public Effect transform(EmployeeEmailUpdated emailUpdated) {
      return effects().produce(emailUpdated.toString());
    }
  }

  @Consume.FromServiceStream(service = "some_service", id = "some_events", ignoreUnknown = true)
  public static class ProtobufEventStreamConsumer extends Consumer {

    public Effect transform(EventsForConsumer.EventForConsumer1 event1) {
      return effects().produce(event1.toString());
    }

    public Effect transform(EventsForConsumer.EventForConsumer2 event2) {
      return effects().produce(event2.toString());
    }
  }

  @Component(id = "employee_view")
  public static class EventStreamSubscriptionView extends View {

    @Consume.FromServiceStream(service = "employee_service", id = "employee_events")
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreate(EmployeeCreated evt) {
        return effects().updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects()
            .updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }
}
