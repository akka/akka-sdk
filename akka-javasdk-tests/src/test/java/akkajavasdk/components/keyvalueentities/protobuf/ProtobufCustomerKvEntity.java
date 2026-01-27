/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.protobuf;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akkajavasdk.protocol.SerializationTestProtos.CustomerState;
import akkajavasdk.protocol.SerializationTestProtos.Status;

/**
 * A key-value entity that uses a protobuf message for state. This demonstrates that protobuf
 * serialization works correctly with key-value entities.
 */
@ComponentId("protobuf-customer-kv")
public class ProtobufCustomerKvEntity extends KeyValueEntity<CustomerState> {

  @Override
  public CustomerState emptyState() {
    return CustomerState.newBuilder().setStatus(Status.UNKNOWN).build();
  }

  // Command handlers

  public Effect<String> create(CreateCommand command) {
    if (!currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer already exists");
    }
    var newState =
        CustomerState.newBuilder()
            .setCustomerId(commandContext().entityId())
            .setName(command.name())
            .setEmail(command.email())
            .setStatus(Status.ACTIVE)
            .build();
    return effects().updateState(newState).thenReply("Customer created");
  }

  public Effect<String> changeName(ChangeNameCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var newState = currentState().toBuilder().setName(command.newName()).build();
    return effects().updateState(newState).thenReply("Name changed");
  }

  public Effect<String> changeEmail(ChangeEmailCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var newState = currentState().toBuilder().setEmail(command.newEmail()).build();
    return effects().updateState(newState).thenReply("Email changed");
  }

  public Effect<String> changeStatus(ChangeStatusCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var newState = currentState().toBuilder().setStatus(command.newStatus()).build();
    return effects().updateState(newState).thenReply("Status changed");
  }

  public Effect<String> addTag(AddTagCommand command) {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    var newState = currentState().toBuilder().addTags(command.tag()).build();
    return effects().updateState(newState).thenReply("Tag added");
  }

  public ReadOnlyEffect<CustomerState> get() {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    return effects().reply(currentState());
  }

  public Effect<String> delete() {
    if (currentState().getCustomerId().isEmpty()) {
      return effects().error("Customer does not exist");
    }
    return effects().deleteEntity().thenReply("Customer deleted");
  }

  // Command records
  public record CreateCommand(String name, String email) {}

  public record ChangeNameCommand(String newName) {}

  public record ChangeEmailCommand(String newEmail) {}

  public record ChangeStatusCommand(Status newStatus) {}

  public record AddTagCommand(String tag) {}
}
