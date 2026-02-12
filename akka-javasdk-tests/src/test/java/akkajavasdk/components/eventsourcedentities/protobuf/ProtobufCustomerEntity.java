/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.protobuf;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.ProtoEventTypes;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerCreated;
import akkajavasdk.protocol.SerializationTestProtos.CustomerEmailChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerNameChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerState;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import com.google.protobuf.GeneratedMessageV3;

/**
 * An event sourced entity that uses protobuf messages for both state and events. This demonstrates
 * that protobuf serialization works correctly with event sourcing.
 */
@ComponentId("protobuf-customer")
@ProtoEventTypes({CustomerCreated.class, CustomerNameChanged.class, CustomerEmailChanged.class})
public class ProtobufCustomerEntity extends EventSourcedEntity<CustomerState, GeneratedMessageV3> {

  @Override
  public CustomerState emptyState() {
    return CustomerState.newBuilder().setStatus(Status.UNKNOWN).build();
  }

  @Override
  public CustomerState applyEvent(GeneratedMessageV3 event) {
    return switch (event) {
      case CustomerCreated created ->
          CustomerState.newBuilder()
              .setCustomerId(created.getCustomerId())
              .setName(created.getName())
              .setEmail(created.getEmail())
              .setStatus(Status.ACTIVE)
              .build();

      case CustomerNameChanged nameChanged ->
          currentState().toBuilder().setName(nameChanged.getNewName()).build();

      case CustomerEmailChanged emailChanged ->
          currentState().toBuilder().setEmail(emailChanged.getNewEmail()).build();

      default ->
          throw new IllegalArgumentException("Unknown event type: " + event.getClass().getName());
    };
  }

  // Command handlers

  public Effect<String> create(CreateCustomerCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      var event =
          CustomerCreated.newBuilder()
              .setCustomerId(commandContext().entityId())
              .setName(command.name())
              .setEmail(command.email())
              .build();
      return effects().persist(event).thenReply(__ -> "Customer created");
    } else {
      return effects().error("Customer already exists");
    }
  }

  public Effect<String> changeName(ChangeNameCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var event =
        CustomerNameChanged.newBuilder()
            .setCustomerId(currentState().getCustomerId())
            .setNewName(command.newName())
            .build();
    return effects().persist(event).thenReply(__ -> "Name changed");
  }

  public Effect<String> changeEmail(ChangeEmailCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var event =
        CustomerEmailChanged.newBuilder()
            .setCustomerId(currentState().getCustomerId())
            .setNewEmail(command.newEmail())
            .build();
    return effects().persist(event).thenReply(__ -> "Email changed");
  }

  public ReadOnlyEffect<CustomerState> getCustomer() {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    return effects().reply(currentState());
  }

  /** Force entity restart - useful for testing event replay. */
  public Effect<String> restart() {
    throw new RuntimeException("Forceful restart for testing!");
  }

  /**
   * Command handler that accepts a protobuf message directly as a parameter. This tests that
   * protobuf messages can be passed through the component client as command parameters.
   */
  public Effect<SimpleMessage> echoProtobuf(SimpleMessage message) {
    // Simply echo back the protobuf message to verify serialization round-trip
    return effects().reply(message);
  }

  /**
   * Command handler that accepts a protobuf message and uses it to create the entity. This tests
   * that protobuf command parameters work correctly with event persistence.
   */
  public Effect<String> createFromProtobuf(SimpleMessage command) {
    if (!currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer already exists");
    }
    var event =
        CustomerCreated.newBuilder()
            .setCustomerId(commandContext().entityId())
            .setName(command.getText())
            .setEmail("from-protobuf@test.com")
            .build();
    return effects()
        .persist(event)
        .thenReply(__ -> "Customer created from protobuf with number: " + command.getNumber());
  }

  /**
   * Command handler that tries to persist an event type NOT listed in @ProtoEventTypes annotation.
   * This should fail at runtime with a validation error.
   */
  public Effect<String> persistInvalidEventType(PersistInvalidEventCommand command) {
    // SimpleMessage is NOT in the @ProtoEventTypes annotation, so this should fail
    var invalidEvent = SimpleMessage.newBuilder().setText(command.text()).setNumber(42).build();
    return effects().persist(invalidEvent).thenReply(__ -> "This should not be reached");
  }

  // Command records (using Java records as input)
  public record CreateCustomerCommand(String name, String email) {}

  public record ChangeNameCommand(String newName) {}

  public record ChangeEmailCommand(String newEmail) {}

  public record PersistInvalidEventCommand(String text) {}
}
