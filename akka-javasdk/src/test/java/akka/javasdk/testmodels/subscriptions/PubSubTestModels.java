/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.subscriptions;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Produce;
import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.testmodels.workflow.WorkflowTestModels.TransferWorkflow;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.testmodels.Done;
import akka.javasdk.testmodels.Message;
import akka.javasdk.testmodels.Message2;
import akka.javasdk.testmodels.eventsourcedentity.Employee;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent.EmployeeCreated;
import akka.javasdk.testmodels.eventsourcedentity.EmployeeEvent.EmployeeEmailUpdated;
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels;
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.CounterEventSourcedEntity;
import akka.javasdk.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity;
import akka.javasdk.testmodels.keyvalueentity.Counter;
import akka.javasdk.testmodels.keyvalueentity.CounterState;

public class PubSubTestModels {//TODO shall we remove this class and move things to ActionTestModels and ViewTestModels

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

  public static class MissingConsumeAnnotationConsumer extends Consumer {

    public Effect methodOne(EmployeeCreated message) {
      return effects().produce(message);
    }

    public Effect methodTwo(EmployeeEmailUpdated message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
  public static class SubscribeToEventSourcedEntity extends Consumer {

    public Effect methodOne(EventSourcedEntitiesTestModels.CounterEvent.IncrementCounter evt) {
      return effects().produce(evt.value());
    }

    public Effect methodTwo(EventSourcedEntitiesTestModels.CounterEvent.DecrementCounter evt) {
      return effects().produce(evt.value());
    }
  }


  @Consume.FromTopic(value = "topicXYZ", ignoreUnknown = true)
  public static class SubscribeToTopicsActionTypeLevel extends Consumer {

    public Effect methodOne(Message message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Message2 message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class SubscribeOnlyOneToEventSourcedEntity extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  @Consume.FromTopic("topic")
  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class MultipleTypeLevelSubscriptions extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class MultipleUpdateMethodsForVETypeLevelSubscription extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(String message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class AmbiguousHandlersVESubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(value = Counter.class)
  public static class AmbiguousDeleteHandlersVESubscriptionInConsumer extends Consumer {

    @DeleteHandler
    public Effect methodOne() {
      return effects().ignore();
    }

    @DeleteHandler
    public Effect methodTwo() {
      return effects().ignore();
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class AmbiguousHandlersVETypeLevelSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class AmbiguousHandlersESSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class AmbiguousHandlersESTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromServiceStream(id = "source", service = "a")
  public static class AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic("source")
  public static class AmbiguousHandlersTopiSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic("source")
  public static class AmbiguousHandlersTopicTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }
  }

  @Produce.ToTopic("test")
  public static class MissingSourceForTopicPublishing extends Consumer {

    public Effect methodOne(String message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
  public static class MissingHandlersWhenSubscribeToEventSourcedEntityConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    public Effect onEvent(EmployeeCreated message) {
      return effects().produce(message.toString());
    }
  }

  @Consume.FromKeyValueEntity(value = Counter.class)
  public static class MissingHandlersWhenSubscribeToKVEConsumer extends Consumer {
    public Effect wrongHandler(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromWorkflow(value = TransferWorkflow.class)
  public static class MissingHandlersWhenSubscribeToWorkflowConsumer extends Consumer {
    public Effect wrongHandler(Integer message) {
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

  @Consume.FromKeyValueEntity(Counter.class)
  @Produce.ToTopic("foobar")
  public static class VEWithPublishToTopic extends Consumer {


    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    @DeleteHandler
    public Effect messageTwo() {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  @Produce.ToTopic("foobar")
  public static class ESWithPublishToTopicConsumer extends Consumer {

    public Effect messageOne(EmployeeCreated created) {
      return effects().produce(new Message(created.firstName));
    }

    public Effect messageTwo(EmployeeEmailUpdated updated) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  @Produce.ToTopic("foobar")
  public static class TypeLevelTopicSubscriptionWithPublishToTopic extends Consumer {

    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    public Effect messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  @Produce.ToTopic("foobar")
  public static class StreamSubscriptionWithPublishToTopic extends Consumer {

    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    public Effect messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class ConsumerWithMethodLevelAclAndSubscription extends Consumer {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public Effect messageOne(Message message) {
      return effects().done();
    }
  }


  // common query parameter for views in this file
  public record ByEmail(String email) {}

  @ComponentId("employee_view")
  public static class SubscribeOnTypeToEventSourcedEvents extends View {

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class, ignoreUnknown = true)
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreate(EmployeeCreated evt) {
        return effects()
            .updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects().updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
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
      return switch (event){
        case EmployeeCreated created ->
          effects().produce(created.toString());
        case EmployeeEmailUpdated emailUpdated ->
          effects().produce(emailUpdated.toString());
      };
    }

  }

  @Consume.FromServiceStream(service = "employee_service", id = "employee_events", ignoreUnknown = true)
  public static class EventStreamSubscriptionConsumer extends Consumer {

    public Effect transform(EmployeeCreated created) {
      return effects().produce(created.toString());
    }

    public Effect transform(EmployeeEmailUpdated emailUpdated) {
      return effects().produce(emailUpdated.toString());
    }
  }

  @ComponentId("employee_view")
  public static class EventStreamSubscriptionView extends View {

    @Consume.FromServiceStream(service = "employee_service", id = "employee_events")
    public static class Employees extends TableUpdater<Employee> {
      public Effect<Employee> onCreate(EmployeeCreated evt) {
        return effects()
            .updateRow(new Employee(evt.firstName, evt.lastName, evt.email));
      }

      public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
        var employee = rowState();
        return effects().updateRow(new Employee(employee.firstName(), employee.lastName(), eeu.email));
      }
    }

    @Query("SELECT * FROM employees WHERE email = :email")
    public QueryEffect<Employee> getEmployeeByEmail(ByEmail byEmail) {
      return queryResult();
    }
  }
}
