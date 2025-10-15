/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

import akka.javasdk.JsonMigration;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Migration;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import java.util.List;

public class EventSourcedEntitiesTestModels {

  public sealed interface Event {

    final class Event1 implements Event {}

    final class Event2 implements Event {}
  }

  public static class NonSealedEvent {}

  public static class ValidEntity extends EventSourcedEntity<String, Event> {
    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> command1(String cmd) {
      return effects().reply(cmd);
    }

    public Effect<String> command2(Integer cmd) {
      return effects().reply(cmd.toString());
    }

    @Override
    public String applyEvent(Event event) {
      return "";
    }
  }

  public static class EntityWithNonSealedEvent extends EventSourcedEntity<String, NonSealedEvent> {
    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> command(String cmd) {
      return effects().reply(cmd);
    }

    @Override
    public String applyEvent(NonSealedEvent event) {
      return "";
    }
  }

  public static class EntityWithDuplicateCommandHandlers extends EventSourcedEntity<String, Event> {
    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> command(String cmd) {
      return effects().reply(cmd);
    }

    public Effect<String> command(Integer cmd) {
      return effects().reply(cmd.toString());
    }

    @Override
    public String applyEvent(Event event) {
      return "";
    }
  }

  public static class EntityWithTwoArgCommandHandler extends EventSourcedEntity<String, Event> {
    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> command(String cmd, int i) {
      return effects().reply(cmd);
    }

    @Override
    public String applyEvent(Event event) {
      return "";
    }
  }

  public static class EntityWithNoEffectMethod extends EventSourcedEntity<String, Event> {
    @Override
    public String emptyState() {
      return "";
    }

    public String command(String cmd) {
      return cmd;
    }

    @Override
    public String applyEvent(Event event) {
      return "";
    }
  }

  public sealed interface CounterEvent {
    record IncrementCounter(int value) implements CounterEvent {}

    record DecrementCounter(int value) implements CounterEvent {}
  }

  @Component(id = "employee")
  public static class EmployeeEntity extends EventSourcedEntity<Employee, EmployeeEvent> {

    public Effect<String> createUser(CreateEmployee create) {
      return effects()
          .persist(
              new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
          .thenReply(__ -> "ok");
    }

    public Employee applyEvent(EmployeeEvent event) {
      EmployeeEvent.EmployeeCreated create = (EmployeeEvent.EmployeeCreated) event;
      return new Employee(create.firstName, create.lastName, create.email);
    }
  }

  @Component(id = "counter-entity")
  public static class CounterEventSourcedEntity extends EventSourcedEntity<Integer, CounterEvent> {

    @Migration(EventMigration.class)
    public record Event(String s) {}

    public static class EventMigration extends JsonMigration {

      public EventMigration() {}

      @Override
      public int currentVersion() {
        return 1;
      }

      @Override
      public List<String> supportedClassNames() {
        return List.of("additional-mapping");
      }
    }

    public ReadOnlyEffect<Integer> getInteger() {
      return effects().reply(currentState());
    }

    public Effect<Integer> changeInteger(Integer number) {
      if (number == 0) {
        return effects().reply(currentState());
      } else if (number < 0) {
        return effects()
            .persist(new CounterEvent.DecrementCounter(number))
            .thenReply(newValue -> newValue);
      } else {
        return effects()
            .persist(new CounterEvent.IncrementCounter(number))
            .thenReply(newValue -> newValue);
      }
    }

    @Override
    public Integer applyEvent(CounterEvent event) {
      return 0;
    }
  }

  @Component(id = "counter")
  public static class InvalidEventSourcedEntityWithOverloadedCommandHandler
      extends EventSourcedEntity<Employee, EmployeeEvent> {

    public Effect<String> createUser(CreateEmployee create) {
      return effects()
          .persist(
              new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
          .thenReply(__ -> "ok");
    }

    public Effect<String> createUser(String email) {
      return effects()
          .persist(new EmployeeEvent.EmployeeCreated("John", "Doe", email))
          .thenReply(__ -> "ok");
    }

    @Override
    public Employee applyEvent(EmployeeEvent event) {
      return null;
    }
  }

  @Component(id = "counter")
  public static class InvalidEventSourcedEntityWithGenericReturnTypeHandler
      extends EventSourcedEntity<Employee, EmployeeEvent> {

    public Effect<List<String>> createUser(CreateEmployee create) {
      return effects()
          .persist(
              new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
          .thenReply(__ -> List.of("ok"));
    }

    public Effect<String> createUser2(CreateEmployee create) {
      return effects()
          .persist(
              new EmployeeEvent.EmployeeCreated(create.firstName, create.lastName, create.email))
          .thenReply(__ -> "ok");
    }

    @Override
    public Employee applyEvent(EmployeeEvent event) {
      return null;
    }
  }
}
