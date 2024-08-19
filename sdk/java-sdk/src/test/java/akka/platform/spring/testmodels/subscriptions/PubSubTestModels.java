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
    public Effect<CounterState> onUpdate(CounterState message) {
      return effects().reply(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class SubscribeToValueEntityTypeLevel extends Consumer {

    public Effect<CounterState> onUpdate(CounterState message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToValueEntityWithDeletes extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect<CounterState> onUpdate(CounterState message) {
      return effects().reply(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect<CounterState> onDelete() {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class SubscribeToEventSourcedEmployee extends Consumer {

    public Effect<EmployeeCreated> methodOne(EmployeeCreated message) {
      return effects().reply(message);
    }

    public Effect<EmployeeEmailUpdated> methodTwo(EmployeeEmailUpdated message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToEventSourcedEntity extends Consumer {

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect<Integer> methodOne(EventSourcedEntitiesTestModels.CounterEvent.IncrementCounter evt) {
      return effects().reply(evt.value());
    }

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect<Integer> methodTwo(EventSourcedEntitiesTestModels.CounterEvent.DecrementCounter evt) {
      return effects().reply(evt.value());
    }
  }

  @Consume.FromTopic(value = "topicAAA", consumerGroup = "aa")
  public static class SubscribeToTopicActionTypeLevelMethodLevel extends Consumer {

    public Effect<Message> messageOne(Message message) {return effects().reply(message);}

    @Consume.FromTopic(value = "topicXYZ")
    public Effect<Message2> messageTwo(Message2 message) {return effects().reply(message);}
  }

  @Consume.FromTopic(value = "topicXYZ", ignoreUnknown = true)
  public static class SubscribeToTopicsActionTypeLevel extends Consumer {

    public Effect<Message> methodOne(Message message) {
      return effects().reply(message);
    }

    public Effect<Message2> methodTwo(Message2 message) {
      return effects().reply(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class SubscribeOnlyOneToEventSourcedEntityActionTypeLevel extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class InvalidSubscribeToEventSourcedEntityConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect<String> methodTwo(String message) {
      return effects().reply(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  @Consume.FromTopic("topic")
  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class MultipleTypeLevelSubscriptions extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class MultipleUpdateMethodsForVETypeLevelSubscription extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersVESubscriptionInConsumer extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromKeyValueEntity(AssignedCounter.class)
    public Effect<Integer> methodThree(Integer message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousDeleteHandlersVESubscriptionInConsumer extends Consumer {

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect<Integer> methodOne() {
      return effects().ignore();
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect<Integer> methodTwo() {
      return effects().ignore();
    }

    @Consume.FromKeyValueEntity(value = AssignedCounter.class, handleDeletes = true)
    public Effect<Integer> methodThree() {
      return effects().ignore();
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class AmbiguousHandlersVETypeLevelSubscriptionInConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersESSubscriptionInConsumer extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(CounterEventSourcedEntity.class)
    public Effect<Integer> methodThree(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class AmbiguousHandlersESTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromServiceStream(id = "source", service = "a")
  public static class AmbiguousHandlersStreamTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersTopiSubscriptionInConsumer extends Consumer {

    @Consume.FromTopic("source")
    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("source")
    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("source-2")
    public Effect<Integer> methodThree(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic("source")
  public static class AmbiguousHandlersTopicTypeLevelSubscriptionInConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class MissingSourceForTopicPublishing extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }
  }

  public static class MissingTopicForVESubscription extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    public Effect<String> methodTwo() {
      return effects().ignore();
    }
  }

  public static class MissingTopicForESSubscription extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class MissingTopicForTypeLevelESSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class MissingTopicForTopicSubscription extends Consumer {

    @Consume.FromTopic("source")
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("source")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class MissingTopicForTopicTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class MissingTopicForStreamSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForVESubscription extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo() {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForESSubscription extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class DifferentTopicForESTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForTopicSubscription extends Consumer {

    @Consume.FromTopic("source")
    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("source")
    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class DifferentTopicForTopicTypeLevelSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class DifferentTopicForStreamSubscription extends Consumer {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
  public static class MissingHandlersWhenSubscribeToEventSourcedEntityConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<String> onEvent(EmployeeCreated message) {
      return effects().reply(message.toString());
    }
  }

  public static class MissingHandlersWhenSubscribeToEventSourcedOnMethodLevelEntityConsumer extends Consumer {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
    public Effect<String> onEvent(EmployeeCreated message) {
      return effects().reply(message.toString());
    }
  }

  public static class SubscribeToTopic extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
  public static class SubscribeToTopicTypeLevel extends Consumer {

    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg", ignoreUnknown = true)
  public static class SubscribeToTopicTypeLevelCombined extends Consumer {

    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    public Effect<String> messageTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTopicCombined extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<String> messageTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class InvalidConsumerGroupsWhenSubscribingToTopicConsumer extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg2")
    public Effect<String> messageTwo(String message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
  public static class InvalidSubscribeToTopicConsumer extends Consumer {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTwoTopics extends Consumer {

    @Consume.FromTopic("topicXYZ")
    public Effect<Message> methodOne(Message message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("topicXYZ")
    public Effect<Message2> methodTwo(Message2 message) {
      return effects().reply(message);
    }

    @Consume.FromTopic("topicXYZ")
    public Effect<Integer> methodThree(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class PublishBytesToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect<byte[]> produce(Message msg) {
      return effects().reply(msg.value().getBytes());
    }
  }

  public static class SubscribeToBytesFromTopic extends Consumer {

    @Consume.FromTopic("foobar")
    public Effect<Done> consume(byte[] bytes) {
      return effects().reply(Done.instance);
    }
  }

  public static class VEWithPublishToTopic extends Consumer {

    @Consume.FromKeyValueEntity(Counter.class)
    @Produce.ToTopic("foobar")
    public Effect<Message> messageOne(String msg) {
      return effects().reply(new Message(msg));
    }

    @Consume.FromKeyValueEntity(value = Counter.class, handleDeletes = true)
    @Produce.ToTopic("foobar")
    public Effect<Message> messageTwo() {
      return effects().ignore();
    }
  }

  public static class ESWithPublishToTopicConsumer extends Consumer {

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("foobar")
    public Effect<Message> messageOne(EmployeeCreated created) {
      return effects().reply(new Message(created.firstName));
    }

    @Consume.FromEventSourcedEntity(EmployeeEntity.class)
    @Produce.ToTopic("foobar")
    public Effect<Message> messageTwo(EmployeeEmailUpdated updated) {
      return effects().ignore();
    }
  }

  @Consume.FromEventSourcedEntity(EmployeeEntity.class)
  public static class TypeLevelESWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect<Message> messageOne(EmployeeCreated created) {
      return effects().reply(new Message(created.firstName));
    }

    @Produce.ToTopic("foobar")
    public Effect<Message> messageTwo(EmployeeEmailUpdated updated) {
      return effects().ignore();
    }
  }

  @Consume.FromTopic("source")
  public static class TypeLevelTopicSubscriptionWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect<Message> messageOne(String msg) {
      return effects().reply(new Message(msg));
    }

    @Produce.ToTopic("foobar")
    public Effect<Message> messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class StreamSubscriptionWithPublishToTopic extends Consumer {

    @Produce.ToTopic("foobar")
    public Effect<Message> messageOne(String msg) {
      return effects().reply(new Message(msg));
    }

    @Produce.ToTopic("foobar")
    public Effect<Message> messageTwo(Integer msg) {
      return effects().ignore();
    }
  }

  @Acl(allow = @Acl.Matcher(service = "test"))
  public static class ActionWithServiceLevelAcl extends Action {
  }


  public static class ActionWithMethodLevelAcl extends Action {
    @Acl(allow = @Acl.Matcher(service = "test"))
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class ActionWithMethodLevelAclAndSubscription extends Action {
    @Acl(allow = @Acl.Matcher(service = "test"))
    @Consume.FromKeyValueEntity(Counter.class)
    public Effect<Message> messageOne(Message message) {
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

    public Effect<String> transform(EmployeeEvent event) {
      return switch (event){
        case EmployeeCreated created ->
          effects().reply(created.toString());
        case EmployeeEmailUpdated emailUpdated ->
          effects().reply(emailUpdated.toString());
      };
    }

  }

  @Consume.FromServiceStream(service = "employee_service", id = "employee_events", ignoreUnknown = true)
  public static class EventStreamSubscriptionConsumer extends Consumer {

    public Effect<String> transform(EmployeeCreated created) {
      return effects().reply(created.toString());
    }

    public Effect<String> transform(EmployeeEmailUpdated emailUpdated) {
      return effects().reply(emailUpdated.toString());
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
