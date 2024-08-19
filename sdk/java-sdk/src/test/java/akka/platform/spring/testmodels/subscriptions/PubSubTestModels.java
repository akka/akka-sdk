/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.subscriptions;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.consumer.Consumer;
import akka.platform.javasdk.view.View;
import akka.platform.spring.testmodels.Done;
import akka.platform.spring.testmodels.Message;
import akka.platform.spring.testmodels.Message2;
import akka.platform.spring.testmodels.eventsourcedentity.Employee;
import akka.platform.spring.testmodels.eventsourcedentity.EmployeeEvent;
import akka.platform.spring.testmodels.eventsourcedentity.EmployeeEvent.EmployeeCreated;
import akka.platform.spring.testmodels.eventsourcedentity.EmployeeEvent.EmployeeEmailUpdated;
import akka.platform.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels;
import akka.platform.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.CounterEventSourcedEntity;
import akka.platform.spring.testmodels.eventsourcedentity.EventSourcedEntitiesTestModels.EmployeeEntity;
import akka.platform.spring.testmodels.keyvalueentity.AssignedCounter;
import akka.platform.spring.testmodels.keyvalueentity.Counter;
import akka.platform.spring.testmodels.keyvalueentity.CounterState;

public class PubSubTestModels {//TODO shall we remove this class and move things to ActionTestModels and ViewTestModels

  public static class SubscribeToValueEntity extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect onUpdate(CounterState message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class SubscribeToValueEntityTypeLevel extends Consumer {

    public Effect onUpdate(CounterState message) {
      return effects().produce(message);
    }
  }

  public static class SubscribeToValueEntityWithDeletes extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect onUpdate(CounterState message) {
      return effects().produce(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
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

  public static class SubscribeToEventSourcedEntity extends Consumer {

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect methodOne(EventSourcedEntitiesTestModels.CounterEvent.IncrementCounter evt) {
      return effects().produce(evt.value());
    }

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect methodTwo(EventSourcedEntitiesTestModels.CounterEvent.DecrementCounter evt) {
      return effects().produce(evt.value());
    }
  }

  @Consume.FromTopic(value = "topicAAA", consumerGroup = "aa")
  public static class SubscribeToTopicActionTypeLevelMethodLevel extends Consumer {

    public Effect messageOne(Message message) {return effects().produce(message);}

    @Consume.FromTopic(value = "topicXYZ")
    public Effect messageTwo(Message2 message) {return effects().produce(message);}
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
  public static class SubscribeOnlyOneToEventSourcedEntityActionTypeLevel extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class InvalidSubscribeToEventSourcedEntityConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect methodTwo(String message) {
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

  public static class AmbiguousHandlersVESubscriptionInConsumer extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromKeyValueEntity(AssignedCounter.class)
    public Effect methodThree(Integer message) {
      return effects().produce(message);
    }
  }

  public static class AmbiguousDeleteHandlersVESubscriptionInConsumer extends Consumer {

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect methodOne() {
      return effects().ignore();
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect methodTwo() {
      return effects().ignore();
    }

    @Consume.FromKeyValueEntity(value = AssignedCounter.class, handleDeletes = true)
    public Effect methodThree() {
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

  public static class AmbiguousHandlersESSubscriptionInConsumer extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect methodThree(Integer message) {
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

  public static class AmbiguousHandlersTopiSubscriptionInConsumer extends Consumer {

    @Consume.FromTopic("source")
    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("source")
    public Effect methodTwo(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("source-2")
    public Effect methodThree(Integer message) {
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

  public static class MissingSourceForTopicPublishing extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }
  }

  public static class MissingTopicForVESubscription extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect methodTwo() {
      return effects().ignore();
    }
  }

  public static class MissingTopicForESSubscription extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class MissingTopicForTypeLevelESSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class MissingTopicForTopicSubscription extends Consumer {

    @Consume.FromTopic("source")
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("source")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class MissingTopicForTopicTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class MissingTopicForStreamSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForVESubscription extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    @Produce.ToTopic("another-topic")
    public Effect methodTwo() {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForESSubscription extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("another-topic")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class DifferentTopicForESTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForTopicSubscription extends Consumer {

    @Consume.FromTopic("source")
    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("source")
    @Produce.ToTopic("another-topic")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class DifferentTopicForTopicTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class DifferentTopicForStreamSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect methodOne(String message) {
      return effects().produce(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect methodTwo(Integer message) {
      return effects().ignore();
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

  public static class MissingHandlersWhenSubscribeToEventSourcedOnMethodLevelEntityConsumer extends Consumer {

    public Effect methodOne(Integer message) {
      return effects().produce(message);
    }

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
    public Effect onEvent(EmployeeCreated message) {
      return effects().produce(message.toString());
    }
  }

  public static class SubscribeToTopic extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect messageOne(Message message) {
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

  public static class SubscribeToTopicCombined extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect messageOne(Message message) {
      return effects().produce(message);
    }

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect messageTwo(String message) {
      return effects().produce(message);
    }
  }

  public static class InvalidConsumerGroupsWhenSubscribingToTopicConsumer extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect messageOne(Message message) {
      return effects().produce(message);
    }

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg2")
    public Effect messageTwo(String message) {
      return effects().produce(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
  public static class InvalidSubscribeToTopicConsumer extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect messageOne(Message message) {
      return effects().produce(message);
    }
  }

  public static class SubscribeToTwoTopics extends Consumer {

    @Consume.FromTopic("topicXYZ")
    public Effect methodOne(Message message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("topicXYZ")
    public Effect methodTwo(Message2 message) {
      return effects().produce(message);
    }

    @Consume.FromTopic("topicXYZ")
    public Effect methodThree(Integer message) {
      return effects().produce(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class PublishBytesToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect produce(Message msg) {
      return effects().produce(msg.value().getBytes());
    }
  }

  public static class SubscribeToBytesFromTopic extends Consumer {

    @Consume.FromTopic("foobar")
    public Effect consume(byte[] bytes) {
      return effects().produce(Done.instance);
    }
  }

  public static class VEWithPublishToTopic extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("foobar")
    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    @Produce.ToTopic("foobar")
    public Effect messageTwo() {
      return effects().ignore();
    }
  }

  public static class ESWithPublishToTopicConsumer extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("foobar")
    public Effect messageOne(EmployeeCreated created) {
      return effects().produce(new Message(created.firstName));
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("foobar")
    public Effect messageTwo(EmployeeEmailUpdated updated) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class TypeLevelESWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect messageOne(EmployeeCreated created) {
      return effects().produce(new Message(created.firstName));
    }

    @Produce.ToTopic("foobar")
    public Effect messageTwo(EmployeeEmailUpdated updated) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class TypeLevelTopicSubscriptionWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    @Produce.ToTopic("foobar")
    public Effect messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class StreamSubscriptionWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect messageOne(String msg) {
      return effects().produce(new Message(msg));
    }

    @Produce.ToTopic("foobar")
    public Effect messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ActionWithServiceLevelAcl extends Action {
  }


  public static class ActionWithMethodLevelAcl extends Action {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public Effect messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class ActionWithMethodLevelAclAndSubscription extends Action {
    @Acl(allow = @Acl.Matcher(service = "test"))
    @Consume.FromKeyValueEntity(Counter.class)
    public Effect messageOne(Message message) {
      return effects().reply(message);
    }
  }


  // common query parameter for views in this file
  public record ByEmail(String email) {}

  @ComponentId("employee_view")
  @Consume.FromEventSourcedEntity(value = EmployeeEntity.class, ignoreUnknown = true)
  public static class SubscribeOnTypeToEventSourcedEvents extends View<Employee> {

    public Effect<Employee> onCreate(EmployeeCreated evt) {
      return effects()
        .updateState(new Employee(evt.firstName, evt.lastName, evt.email));
    }

    public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
      var employee = viewState();
      return effects().updateState(new Employee(employee.firstName(), employee.lastName(), eeu.email));
    }

    @Query("SELECT * FROM employees_table WHERE email = :email")
    public Employee getEmployeeByEmail(ByEmail byEmail) {
      return null;
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
  @Consume.FromServiceStream(service = "employee_service", id = "employee_events")
  public static class EventStreamSubscriptionView extends View<Employee> {

    public Effect<Employee> onCreate(EmployeeCreated evt) {
      return effects()
        .updateState(new Employee(evt.firstName, evt.lastName, evt.email));
    }

    public Effect<Employee> onEmailUpdate(EmployeeEmailUpdated eeu) {
      var employee = viewState();
      return effects().updateState(new Employee(employee.firstName(), employee.lastName(), eeu.email));
    }

    @Query("SELECT * FROM employees_table WHERE email = :email")
    public Employee getEmployeeByEmail(ByEmail byEmail) {
      return null;
    }
  }
}
