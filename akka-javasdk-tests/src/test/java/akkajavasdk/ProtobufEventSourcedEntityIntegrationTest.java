/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.ChangeEmailCommand;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.ChangeNameCommand;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.CreateCustomerCommand;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.PersistInvalidEventCommand;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for event sourced entities using protobuf messages for events and state. These
 * tests verify that protobuf events are correctly serialized, persisted, and replayed after entity
 * restart.
 */
@ExtendWith(Junit5LogCapturing.class)
public class ProtobufEventSourcedEntityIntegrationTest extends TestKitSupport {

  @Test
  public void shouldReplayProtobufEventsAfterRestart() {
    var customerId = "replay-test-customer";
    var client = componentClient.forEventSourcedEntity(customerId);

    // Create customer with protobuf event
    var createResult =
        client
            .method(ProtobufCustomerEntity::create)
            .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));
    assertThat(createResult).isEqualTo("Customer created");

    // Change name - persists another protobuf event
    var nameResult =
        client.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("Jane Doe"));
    assertThat(nameResult).isEqualTo("Name changed");

    // Change email - persists another protobuf event
    var emailResult =
        client
            .method(ProtobufCustomerEntity::changeEmail)
            .invoke(new ChangeEmailCommand("jane@newdomain.com"));
    assertThat(emailResult).isEqualTo("Email changed");

    // Verify state before restart
    var stateBefore = client.method(ProtobufCustomerEntity::getCustomer).invoke();
    assertThat(stateBefore.getCustomerId()).isEqualTo(customerId);
    assertThat(stateBefore.getName()).isEqualTo("Jane Doe");
    assertThat(stateBefore.getEmail()).isEqualTo("jane@newdomain.com");
    assertThat(stateBefore.getStatus()).isEqualTo(Status.ACTIVE);

    // Force entity restart - this will cause all protobuf events to be replayed
    restartEntity(client);

    // After restart, events should be replayed and state should be restored
    // This tests the deserialization of protobuf events from the journal
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .until(
            () -> {
              var state = client.method(ProtobufCustomerEntity::getCustomer).invoke();
              return state.getName();
            },
            new IsEqual<>("Jane Doe"));

    // Verify full state after replay
    var stateAfter = client.method(ProtobufCustomerEntity::getCustomer).invoke();
    assertThat(stateAfter.getCustomerId()).isEqualTo(customerId);
    assertThat(stateAfter.getName()).isEqualTo("Jane Doe");
    assertThat(stateAfter.getEmail()).isEqualTo("jane@newdomain.com");
    assertThat(stateAfter.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldReplayMultipleProtobufEventsInCorrectOrder() {
    var customerId = "multiple-events-replay";
    var client = componentClient.forEventSourcedEntity(customerId);

    // Create customer
    client
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("Name1", "email1@test.com"));

    // Multiple name changes to verify event ordering is preserved
    client.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("Name2"));
    client.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("Name3"));
    client.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("FinalName"));

    // Multiple email changes
    client
        .method(ProtobufCustomerEntity::changeEmail)
        .invoke(new ChangeEmailCommand("email2@test.com"));
    client
        .method(ProtobufCustomerEntity::changeEmail)
        .invoke(new ChangeEmailCommand("final@test.com"));

    // Verify state before restart
    var stateBefore = client.method(ProtobufCustomerEntity::getCustomer).invoke();
    assertThat(stateBefore.getName()).isEqualTo("FinalName");
    assertThat(stateBefore.getEmail()).isEqualTo("final@test.com");

    // Force entity restart
    restartEntity(client);

    // After restart, verify events were replayed in correct order
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .until(
            () -> client.method(ProtobufCustomerEntity::getCustomer).invoke().getName(),
            new IsEqual<>("FinalName"));

    var stateAfter = client.method(ProtobufCustomerEntity::getCustomer).invoke();
    assertThat(stateAfter.getName()).isEqualTo("FinalName");
    assertThat(stateAfter.getEmail()).isEqualTo("final@test.com");
  }

  @Test
  public void shouldRejectEventTypeNotInProtoEventTypesAnnotation() {
    var customerId = "invalid-event-type-test";
    var client = componentClient.forEventSourcedEntity(customerId);

    // Try to persist an event type (SimpleMessage) that is NOT in the @ProtoEventTypes annotation
    // This should fail with an IllegalArgumentException
    assertThatThrownBy(
            () ->
                client
                    .method(ProtobufCustomerEntity::persistInvalidEventType)
                    .invoke(new PersistInvalidEventCommand("test")))
        .hasMessageContaining("not declared in @ProtoEventTypes")
        .hasMessageContaining("SimpleMessage");
  }

  private void restartEntity(EventSourcedEntityClient client) {
    try {
      client.method(ProtobufCustomerEntity::restart).invoke();
      fail("This should not be reached - restart throws exception");
    } catch (Exception ignored) {
      // Expected - restart throws RuntimeException to force entity restart
    }
  }
}
