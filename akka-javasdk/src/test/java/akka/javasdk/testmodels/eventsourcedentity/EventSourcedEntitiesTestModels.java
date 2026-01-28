/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.eventsourcedentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ProtoEventTypes;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.google.protobuf.GeneratedMessageV3;
import protoconsumer.EventsForConsumer.EventForConsumer1;
import protoconsumer.EventsForConsumer.EventForConsumer2;

public class EventSourcedEntitiesTestModels {

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

  /**
   * Valid event sourced entity with @ProtoEventTypes annotation and applyEvent accepting
   * GeneratedMessageV3.
   */
  @Component(id = "valid-proto-entity")
  @ProtoEventTypes({EventForConsumer1.class, EventForConsumer2.class})
  public static class ValidProtoEventSourcedEntity
      extends EventSourcedEntity<String, GeneratedMessageV3> {

    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> create(String text) {
      return effects()
          .persist(EventForConsumer1.newBuilder().setText(text).build())
          .thenReply(__ -> "ok");
    }

    @Override
    public String applyEvent(GeneratedMessageV3 event) {
      return switch (event) {
        case EventForConsumer1 e1 -> e1.getText();
        case EventForConsumer2 e2 -> e2.getText();
        default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
      };
    }
  }

  /**
   * Invalid event sourced entity with @ProtoEventTypes annotation but applyEvent accepting
   * EmployeeEvent (not GeneratedMessageV3). This should fail validation.
   */
  @Component(id = "invalid-proto-entity-wrong-event-type")
  @ProtoEventTypes({EventForConsumer1.class, EventForConsumer2.class})
  public static class InvalidProtoEventSourcedEntityWrongEventType
      extends EventSourcedEntity<String, EmployeeEvent> {

    @Override
    public String emptyState() {
      return "";
    }

    public Effect<String> create(String text) {
      return effects()
          .persist(new EmployeeEvent.EmployeeCreated(text, text, text))
          .thenReply(__ -> "ok");
    }

    @Override
    public String applyEvent(EmployeeEvent event) {
      return "test";
    }
  }
}
