/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventSourcedTestKit;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.*;
import akkajavasdk.protocol.SerializationTestProtos.CustomerCreated;
import akkajavasdk.protocol.SerializationTestProtos.CustomerEmailChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerNameChanged;
import akkajavasdk.protocol.SerializationTestProtos.CustomerState;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import com.google.protobuf.GeneratedMessageV3;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests for event sourced entities using protobuf messages for events and state. */
public class ProtobufEventSourcedEntityTest {

  @Test
  public void shouldCreateCustomerWithProtobufEvent() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    var result =
        testKit
            .method(ProtobufCustomerEntity::create)
            .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Customer created");

    // Verify the event was persisted
    assertThat(result.didPersistEvents()).isTrue();
    assertThat(result.getAllEvents()).hasSize(1);

    var event = (CustomerCreated) result.getAllEvents().get(0);
    assertThat(event.getCustomerId()).isEqualTo("testkit-entity-id");
    assertThat(event.getName()).isEqualTo("John Doe");
    assertThat(event.getEmail()).isEqualTo("john@example.com");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEqualTo("testkit-entity-id");
    assertThat(state.getName()).isEqualTo("John Doe");
    assertThat(state.getEmail()).isEqualTo("john@example.com");
    assertThat(state.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldChangeNameWithProtobufEvent() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    // First create the customer
    testKit
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    // Then change the name
    var result =
        testKit
            .method(ProtobufCustomerEntity::changeName)
            .invoke(new ChangeNameCommand("Jane Doe"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Name changed");

    var event = (CustomerNameChanged) result.getAllEvents().get(0);
    assertThat(event.getNewName()).isEqualTo("Jane Doe");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("Jane Doe");
    assertThat(state.getEmail()).isEqualTo("john@example.com"); // Email unchanged
  }

  @Test
  public void shouldChangeEmailWithProtobufEvent() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    // First create the customer
    testKit
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    // Then change the email
    var result =
        testKit
            .method(ProtobufCustomerEntity::changeEmail)
            .invoke(new ChangeEmailCommand("john.doe@newdomain.com"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Email changed");

    var event = (CustomerEmailChanged) result.getAllEvents().get(0);
    assertThat(event.getNewEmail()).isEqualTo("john.doe@newdomain.com");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("John Doe"); // Name unchanged
    assertThat(state.getEmail()).isEqualTo("john.doe@newdomain.com");
  }

  @Test
  public void shouldGetCustomerState() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    // First create the customer
    testKit
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    // Get the customer
    var result = testKit.method(ProtobufCustomerEntity::getCustomer).invoke();

    assertThat(result.isReply()).isTrue();
    var customer = result.getReply();
    assertThat(customer.getName()).isEqualTo("John Doe");
    assertThat(customer.getEmail()).isEqualTo("john@example.com");
    assertThat(customer.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldRejectDuplicateCreate() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    // Create customer
    testKit
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    // Try to create again
    var result =
        testKit
            .method(ProtobufCustomerEntity::create)
            .invoke(new CreateCustomerCommand("Jane Doe", "jane@example.com"));

    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).isEqualTo("Customer already exists");
  }

  @Test
  public void shouldRejectChangeNameForNonExistentCustomer() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    var result =
        testKit
            .method(ProtobufCustomerEntity::changeName)
            .invoke(new ChangeNameCommand("Jane Doe"));

    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).isEqualTo("Customer does not exist");
  }

  @Test
  public void shouldReplayEventsAndRestoreState() {
    // Create testkit with initial events
    var initialEvents =
        List.<GeneratedMessageV3>of(
            CustomerCreated.newBuilder()
                .setCustomerId("cust-123")
                .setName("Original Name")
                .setEmail("original@example.com")
                .build(),
            CustomerNameChanged.newBuilder()
                .setCustomerId("cust-123")
                .setNewName("Updated Name")
                .build());

    var testKit =
        EventSourcedTestKit.ofEntityFromEvents(
            "cust-123", ctx -> new ProtobufCustomerEntity(), initialEvents);

    // Verify state was restored from events
    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEqualTo("cust-123");
    assertThat(state.getName()).isEqualTo("Updated Name");
    assertThat(state.getEmail()).isEqualTo("original@example.com");
    assertThat(state.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldHandleMultipleEventsInSequence() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    // Create customer
    testKit
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    // Multiple changes
    testKit.method(ProtobufCustomerEntity::changeName).invoke(new ChangeNameCommand("John Smith"));

    testKit
        .method(ProtobufCustomerEntity::changeEmail)
        .invoke(new ChangeEmailCommand("john.smith@example.com"));

    testKit
        .method(ProtobufCustomerEntity::changeName)
        .invoke(new ChangeNameCommand("Johnny Smith"));

    // Verify all events were persisted
    var allEvents = testKit.getAllEvents();
    assertThat(allEvents).hasSize(4);
    assertThat(allEvents.get(0)).isInstanceOf(CustomerCreated.class);
    assertThat(allEvents.get(1)).isInstanceOf(CustomerNameChanged.class);
    assertThat(allEvents.get(2)).isInstanceOf(CustomerEmailChanged.class);
    assertThat(allEvents.get(3)).isInstanceOf(CustomerNameChanged.class);

    // Verify final state
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("Johnny Smith");
    assertThat(state.getEmail()).isEqualTo("john.smith@example.com");
  }

  @Test
  public void shouldStartWithEmptyProtobufState() {
    var testKit = EventSourcedTestKit.of(ProtobufCustomerEntity::new);

    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEmpty();
    assertThat(state.getName()).isEmpty();
    assertThat(state.getEmail()).isEmpty();
    assertThat(state.getStatus()).isEqualTo(Status.UNKNOWN);
  }

  @Test
  public void shouldWorkWithCustomEntityId() {
    var testKit = EventSourcedTestKit.of("custom-customer-id", ctx -> new ProtobufCustomerEntity());

    var result =
        testKit
            .method(ProtobufCustomerEntity::create)
            .invoke(new CreateCustomerCommand("John Doe", "john@example.com"));

    var event = (CustomerCreated) result.getAllEvents().get(0);
    assertThat(event.getCustomerId()).isEqualTo("custom-customer-id");

    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEqualTo("custom-customer-id");
  }

  @Test
  public void shouldWorkWithInitialProtobufState() {
    var initialState =
        CustomerState.newBuilder()
            .setCustomerId("existing-customer")
            .setName("Existing Customer")
            .setEmail("existing@example.com")
            .setStatus(Status.ACTIVE)
            .build();

    var testKit =
        EventSourcedTestKit.ofEntityWithState(
            "existing-customer", ctx -> new ProtobufCustomerEntity(), initialState);

    // Try to create - should fail because customer exists
    var createResult =
        testKit
            .method(ProtobufCustomerEntity::create)
            .invoke(new CreateCustomerCommand("New Name", "new@example.com"));

    assertThat(createResult.isError()).isTrue();
    assertThat(createResult.getError()).isEqualTo("Customer already exists");

    // But should be able to change name
    var changeResult =
        testKit
            .method(ProtobufCustomerEntity::changeName)
            .invoke(new ChangeNameCommand("Updated Name"));

    assertThat(changeResult.isReply()).isTrue();
    assertThat(testKit.getState().getName()).isEqualTo("Updated Name");
  }
}
