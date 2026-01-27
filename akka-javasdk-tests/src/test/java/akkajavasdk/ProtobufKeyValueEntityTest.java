/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity.*;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import org.junit.jupiter.api.Test;

/** Tests for key-value entities using protobuf messages for state. */
public class ProtobufKeyValueEntityTest {

  @Test
  public void shouldCreateCustomerWithProtobufState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    var result =
        testKit
            .method(ProtobufCustomerKvEntity::create)
            .invoke(new CreateCommand("John Doe", "john@example.com"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Customer created");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEqualTo("testkit-entity-id");
    assertThat(state.getName()).isEqualTo("John Doe");
    assertThat(state.getEmail()).isEqualTo("john@example.com");
    assertThat(state.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldChangeNameWithProtobufState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Then change the name
    var result =
        testKit
            .method(ProtobufCustomerKvEntity::changeName)
            .invoke(new ChangeNameCommand("Jane Doe"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Name changed");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("Jane Doe");
    assertThat(state.getEmail()).isEqualTo("john@example.com"); // Email unchanged
  }

  @Test
  public void shouldChangeEmailWithProtobufState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Then change the email
    var result =
        testKit
            .method(ProtobufCustomerKvEntity::changeEmail)
            .invoke(new ChangeEmailCommand("john.doe@newdomain.com"));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Email changed");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("John Doe"); // Name unchanged
    assertThat(state.getEmail()).isEqualTo("john.doe@newdomain.com");
  }

  @Test
  public void shouldChangeStatusWithProtobufEnum() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Then change the status
    var result =
        testKit
            .method(ProtobufCustomerKvEntity::changeStatus)
            .invoke(new ChangeStatusCommand(Status.INACTIVE));

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Status changed");

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getStatus()).isEqualTo(Status.INACTIVE);
  }

  @Test
  public void shouldAddTagsToProtobufState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Add multiple tags
    testKit.method(ProtobufCustomerKvEntity::addTag).invoke(new AddTagCommand("premium"));
    testKit.method(ProtobufCustomerKvEntity::addTag).invoke(new AddTagCommand("verified"));
    testKit.method(ProtobufCustomerKvEntity::addTag).invoke(new AddTagCommand("early-adopter"));

    // Verify state was updated
    var state = testKit.getState();
    assertThat(state.getTagsList()).containsExactly("premium", "verified", "early-adopter");
  }

  @Test
  public void shouldGetCustomerState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Get the customer
    var result = testKit.method(ProtobufCustomerKvEntity::get).invoke();

    assertThat(result.isReply()).isTrue();
    var customer = result.getReply();
    assertThat(customer.getName()).isEqualTo("John Doe");
    assertThat(customer.getEmail()).isEqualTo("john@example.com");
    assertThat(customer.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldDeleteCustomer() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // First create the customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Delete the customer
    var result = testKit.method(ProtobufCustomerKvEntity::delete).invoke();

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("Customer deleted");
    assertThat(result.stateWasDeleted()).isTrue();
    assertThat(testKit.isDeleted()).isTrue();
  }

  @Test
  public void shouldRejectDuplicateCreate() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // Create customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Try to create again
    var result =
        testKit
            .method(ProtobufCustomerKvEntity::create)
            .invoke(new CreateCommand("Jane Doe", "jane@example.com"));

    assertThat(result.isError()).isTrue();
    assertThat(result.getError()).isEqualTo("Customer already exists");
  }

  @Test
  public void shouldRejectOperationsOnNonExistentCustomer() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    var nameResult =
        testKit
            .method(ProtobufCustomerKvEntity::changeName)
            .invoke(new ChangeNameCommand("Jane Doe"));
    assertThat(nameResult.isError()).isTrue();
    assertThat(nameResult.getError()).isEqualTo("Customer does not exist");

    var getResult = testKit.method(ProtobufCustomerKvEntity::get).invoke();
    assertThat(getResult.isError()).isTrue();
    assertThat(getResult.getError()).isEqualTo("Customer does not exist");

    var deleteResult = testKit.method(ProtobufCustomerKvEntity::delete).invoke();
    assertThat(deleteResult.isError()).isTrue();
    assertThat(deleteResult.getError()).isEqualTo("Customer does not exist");
  }

  @Test
  public void shouldStartWithEmptyProtobufState() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEmpty();
    assertThat(state.getName()).isEmpty();
    assertThat(state.getEmail()).isEmpty();
    assertThat(state.getStatus()).isEqualTo(Status.UNKNOWN);
    assertThat(state.getTagsList()).isEmpty();
  }

  @Test
  public void shouldWorkWithCustomEntityId() {
    var testKit = KeyValueEntityTestKit.of("custom-kv-id", ctx -> new ProtobufCustomerKvEntity());

    var result =
        testKit
            .method(ProtobufCustomerKvEntity::create)
            .invoke(new CreateCommand("John Doe", "john@example.com"));

    assertThat(result.isReply()).isTrue();
    var state = testKit.getState();
    assertThat(state.getCustomerId()).isEqualTo("custom-kv-id");
  }

  @Test
  public void shouldHandleMultipleStateUpdates() {
    var testKit = KeyValueEntityTestKit.of(ctx -> new ProtobufCustomerKvEntity());

    // Create customer
    testKit
        .method(ProtobufCustomerKvEntity::create)
        .invoke(new CreateCommand("John Doe", "john@example.com"));

    // Multiple updates
    testKit
        .method(ProtobufCustomerKvEntity::changeName)
        .invoke(new ChangeNameCommand("John Smith"));
    testKit
        .method(ProtobufCustomerKvEntity::changeEmail)
        .invoke(new ChangeEmailCommand("john.smith@example.com"));
    testKit
        .method(ProtobufCustomerKvEntity::changeStatus)
        .invoke(new ChangeStatusCommand(Status.PENDING));
    testKit.method(ProtobufCustomerKvEntity::addTag).invoke(new AddTagCommand("updated"));

    // Verify final state
    var state = testKit.getState();
    assertThat(state.getName()).isEqualTo("John Smith");
    assertThat(state.getEmail()).isEqualTo("john.smith@example.com");
    assertThat(state.getStatus()).isEqualTo(Status.PENDING);
    assertThat(state.getTagsList()).containsExactly("updated");
  }
}
