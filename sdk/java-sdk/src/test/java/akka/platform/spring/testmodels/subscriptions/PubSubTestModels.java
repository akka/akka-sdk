/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.subscriptions;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Acl;
import akka.platform.javasdk.annotations.Produce;
import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ViewId;
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

  public static class SubscribeToValueEntityAction extends Action {

    @Consume.FromKeyValueEntity(Counter.class)
    public Effect<CounterState> onUpdate(CounterState message) {
      return effects().reply(message);
    }
  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class SubscribeToValueEntityTypeLevelAction extends Action {

    public Effect<CounterState> onUpdate(CounterState message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToValueEntityWithDeletesAction extends Action {

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
  public static class SubscribeToEventSourcedEmployee extends Action {

    public Effect<EmployeeCreated> methodOne(EmployeeCreated message) {
      return effects().reply(message);
    }

    public Effect<EmployeeEmailUpdated> methodTwo(EmployeeEmailUpdated message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToEventSourcedEntityAction extends Action {

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
  public static class SubscribeToTopicActionTypeLevelMethodLevel extends Action {

    public Effect<Message> messageOne(Message message) {return effects().reply(message);}

    @Consume.FromTopic(value = "topicXYZ")
    public Effect<Message2> messageTwo(Message2 message) {return effects().reply(message);}
  }

  @Consume.FromTopic(value = "topicXYZ", ignoreUnknown = true)
  public static class SubscribeToTopicsActionTypeLevel extends Action {

    public Effect<Message> methodOne(Message message) {
      return effects().reply(message);
    }

    public Effect<Message2> methodTwo(Message2 message) {
      return effects().reply(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class SubscribeOnlyOneToEventSourcedEntityActionTypeLevel extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromEventSourcedEntity(value = CounterEventSourcedEntity.class, ignoreUnknown = true)
  public static class InvalidSubscribeToEventSourcedEntityAction extends Action {

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
  public static class MultipleTypeLevelSubscriptionsInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

  }

  @Consume.FromKeyValueEntity(Counter.class)
  public static class MultipleUpdateMethodsForVETypeLevelSubscriptionInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersVESubscriptionInAction extends Action {

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

  public static class AmbiguousDeleteHandlersVESubscriptionInAction extends Action {

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
  public static class AmbiguousHandlersVETypeLevelSubscriptionInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersESSubscriptionInAction extends Action {

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
  public static class AmbiguousHandlersESTypeLevelSubscriptionInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  @Consume.FromServiceStream(id = "source", service = "a")
  public static class AmbiguousHandlersStreamTypeLevelSubscriptionInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class AmbiguousHandlersTopiSubscriptionInAction extends Action {

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
  public static class AmbiguousHandlersTopicTypeLevelSubscriptionInAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<Integer> methodTwo(Integer message) {
      return effects().reply(message);
    }
  }

  public static class MissingSourceForTopicPublishing extends Action {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }
  }

  public static class MissingTopicForVESubscription extends Action {

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

  public static class MissingTopicForESSubscription extends Action {

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
  public static class MissingTopicForTypeLevelESSubscription extends Action {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class MissingTopicForTopicSubscription extends Action {

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
  public static class MissingTopicForTopicTypeLevelSubscription extends Action {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  @Consume.FromServiceStream(id = "source", service = "abc")
  public static class MissingTopicForStreamSubscription extends Action {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForVESubscription extends Action {

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

  public static class DifferentTopicForESSubscription extends Action {

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
  public static class DifferentTopicForESTypeLevelSubscription extends Action {

    @Produce.ToTopic("test")
    public Effect<String> methodOne(String message) {
      return effects().reply(message);
    }

    @Produce.ToTopic("another-topic")
    public Effect<String> methodTwo(Integer message) {
      return effects().ignore();
    }
  }

  public static class DifferentTopicForTopicSubscription extends Action {

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
  public static class DifferentTopicForTopicTypeLevelSubscription extends Action {

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
  public static class DifferentTopicForStreamSubscription extends Action {

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
  public static class MissingHandlersWhenSubscribeToEventSourcedEntityAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    public Effect<String> onEvent(EmployeeCreated message) {
      return effects().reply(message.toString());
    }
  }

  public static class MissingHandlersWhenSubscribeToEventSourcedOnMethodLevelEntityAction extends Action {

    public Effect<Integer> methodOne(Integer message) {
      return effects().reply(message);
    }

    @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
    public Effect<String> onEvent(EmployeeCreated message) {
      return effects().reply(message.toString());
    }
  }

  public static class SubscribeToTopicAction extends Action {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
  public static class SubscribeToTopicTypeLevelAction extends Action {

    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg", ignoreUnknown = true)
  public static class SubscribeToTopicTypeLevelCombinedAction extends Action {

    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    public Effect<String> messageTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTopicCombinedAction extends Action {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<String> messageTwo(String message) {
      return effects().reply(message);
    }
  }

  public static class InvalidConsumerGroupsWhenSubscribingToTopicAction extends Action {

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
  public static class InvalidSubscribeToTopicAction extends Action {

    @Consume.FromTopic(value = "topicXYZ", consumerGroup = "cg")
    public Effect<Message> messageOne(Message message) {
      return effects().reply(message);
    }
  }

  public static class SubscribeToTwoTopicsAction extends Action {

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
  public static class PublishBytesToTopicAction extends Action {

    @Produce.ToTopic("foobar")
    public Effect<byte[]> produce(Message msg) {
      return effects().reply(msg.value().getBytes());
    }
  }

  public static class SubscribeToBytesFromTopicAction extends Action {

    @Consume.FromTopic("foobar")
    public Effect<Done> consume(byte[] bytes) {
      return effects().reply(Done.instance);
    }
  }

  public static class VEWithPublishToTopicAction extends Action {

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

  public static class ESWithPublishToTopicAction extends Action {

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
  public static class TypeLevelESWithPublishToTopicAction extends Action {

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
  public static class TypeLevelTopicSubscriptionWithPublishToTopicAction extends Action {

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
  public static class StreamSubscriptionWithPublishToTopicAction extends Action {

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

  @ViewId("employee_view")
  @Table("employee_table")
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

    @Query("SELECT * FROM employees_view WHERE email = :email")
    public Employee getEmployeeByEmail(ByEmail byEmail) {
      return null;
    }
  }


  @Consume.FromEventSourcedEntity(value = EmployeeEntity.class)
  @Produce.ServiceStream(id = "employee_events")
  public static class EventStreamPublishingAction extends Action {

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
  public static class EventStreamSubscriptionAction extends Action {

    public Effect<String> transform(EmployeeCreated created) {
      return effects().reply(created.toString());
    }

    public Effect<String> transform(EmployeeEmailUpdated emailUpdated) {
      return effects().reply(emailUpdated.toString());
    }
  }

  @ViewId("employee_view")
  @Table("employee_table")
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

    @Query("SELECT * FROM employees_view WHERE email = :email")
    public Employee getEmployeeByEmail(ByEmail byEmail) {
      return null;
    }
  }
}
