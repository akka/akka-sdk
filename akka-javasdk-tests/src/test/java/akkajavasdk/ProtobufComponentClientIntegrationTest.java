/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.actions.ProtoInputTimedAction;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import akkajavasdk.protocol.SerializationTestProtos.SimpleMessage;
import akkajavasdk.protocol.SerializationTestProtos.Status;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for protobuf message parameters passed through the component client. These
 * tests verify that protobuf messages can be correctly serialized and deserialized when used as
 * command parameters in test-to-component communication via the runtime.
 */
@ExtendWith(Junit5LogCapturing.class)
public class ProtobufComponentClientIntegrationTest extends TestKitSupport {

  // ============================================================
  // Direct component client calls with protobuf parameters
  // ============================================================

  @Test
  public void shouldPassProtobufMessageToKvEntityViaComponentClient() {
    var entityId = "kv-proto-param-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder().setText("Hello KV").setNumber(42).setFlag(true).build();

    // Call entity method that accepts protobuf message directly
    var result =
        componentClient
            .forKeyValueEntity(entityId)
            .method(ProtobufCustomerKvEntity::echoProtobuf)
            .invoke(message);

    // Verify the protobuf message was correctly passed and returned
    assertThat(result.getText()).isEqualTo("Hello KV");
    assertThat(result.getNumber()).isEqualTo(42);
    assertThat(result.getFlag()).isTrue();
  }

  @Test
  public void shouldPassProtobufMessageToEsEntityViaComponentClient() {
    var entityId = "es-proto-param-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder().setText("Hello ES").setNumber(123).setFlag(false).build();

    // Call entity method that accepts protobuf message directly
    var result =
        componentClient
            .forEventSourcedEntity(entityId)
            .method(ProtobufCustomerEntity::echoProtobuf)
            .invoke(message);

    // Verify the protobuf message was correctly passed and returned
    assertThat(result.getText()).isEqualTo("Hello ES");
    assertThat(result.getNumber()).isEqualTo(123);
    assertThat(result.getFlag()).isFalse();
  }

  @Test
  public void shouldCreateKvEntityWithProtobufParameter() {
    var entityId = "kv-create-proto-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder()
            .setText("Proto Customer Name")
            .setNumber(999)
            .setFlag(true)
            .build();

    // Create entity using protobuf message parameter
    var createResult =
        componentClient
            .forKeyValueEntity(entityId)
            .method(ProtobufCustomerKvEntity::createFromProtobuf)
            .invoke(message);

    assertThat(createResult).isEqualTo("Customer created from protobuf with number: 999");

    // Verify state was correctly created
    var state =
        componentClient.forKeyValueEntity(entityId).method(ProtobufCustomerKvEntity::get).invoke();

    assertThat(state.getCustomerId()).isEqualTo(entityId);
    assertThat(state.getName()).isEqualTo("Proto Customer Name");
    assertThat(state.getEmail()).isEqualTo("from-protobuf@test.com");
    assertThat(state.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldCreateEsEntityWithProtobufParameter() {
    var entityId = "es-create-proto-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder()
            .setText("ES Proto Customer")
            .setNumber(888)
            .setFlag(false)
            .build();

    // Create entity using protobuf message parameter
    var createResult =
        componentClient
            .forEventSourcedEntity(entityId)
            .method(ProtobufCustomerEntity::createFromProtobuf)
            .invoke(message);

    assertThat(createResult).isEqualTo("Customer created from protobuf with number: 888");

    // Verify state was correctly created
    var state =
        componentClient
            .forEventSourcedEntity(entityId)
            .method(ProtobufCustomerEntity::getCustomer)
            .invoke();

    assertThat(state.getCustomerId()).isEqualTo(entityId);
    assertThat(state.getName()).isEqualTo("ES Proto Customer");
    assertThat(state.getEmail()).isEqualTo("from-protobuf@test.com");
    assertThat(state.getStatus()).isEqualTo(Status.ACTIVE);
  }

  @Test
  public void shouldHandleEmptyProtobufMessage() {
    var entityId = "kv-empty-proto-" + UUID.randomUUID();
    var message = SimpleMessage.getDefaultInstance();

    // Call entity with empty protobuf message
    var result =
        componentClient
            .forKeyValueEntity(entityId)
            .method(ProtobufCustomerKvEntity::echoProtobuf)
            .invoke(message);

    // Verify default values
    assertThat(result.getText()).isEmpty();
    assertThat(result.getNumber()).isEqualTo(0);
    assertThat(result.getFlag()).isFalse();
  }

  @Test
  public void shouldHandleProtobufMessageWithSpecialCharacters() {
    var entityId = "kv-special-chars-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder()
            .setText("Special chars: äöü ñ 中文 🎉 <>&\"'")
            .setNumber(-12345)
            .setFlag(true)
            .build();

    var result =
        componentClient
            .forKeyValueEntity(entityId)
            .method(ProtobufCustomerKvEntity::echoProtobuf)
            .invoke(message);

    assertThat(result.getText()).isEqualTo("Special chars: äöü ñ 中文 🎉 <>&\"'");
    assertThat(result.getNumber()).isEqualTo(-12345);
    assertThat(result.getFlag()).isTrue();
  }

  // ============================================================
  // Multiple calls and state persistence with protobuf params
  // ============================================================

  @Test
  public void shouldHandleMultipleProtobufCallsToSameEntity() {
    var entityId = "kv-multi-proto-" + UUID.randomUUID();

    // Make multiple calls with different protobuf messages
    for (int i = 1; i <= 5; i++) {
      var message =
          SimpleMessage.newBuilder()
              .setText("Call " + i)
              .setNumber(i * 100)
              .setFlag(i % 2 == 0)
              .build();

      var result =
          componentClient
              .forKeyValueEntity(entityId)
              .method(ProtobufCustomerKvEntity::echoProtobuf)
              .invoke(message);

      assertThat(result.getText()).isEqualTo("Call " + i);
      assertThat(result.getNumber()).isEqualTo(i * 100);
      assertThat(result.getFlag()).isEqualTo(i % 2 == 0);
    }
  }

  @Test
  public void shouldPersistStateFromProtobufParameterAfterRestart() {
    var entityId = "kv-persist-proto-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder()
            .setText("Persistent Proto Name")
            .setNumber(12345)
            .setFlag(true)
            .build();

    // Create entity with protobuf parameter
    componentClient
        .forKeyValueEntity(entityId)
        .method(ProtobufCustomerKvEntity::createFromProtobuf)
        .invoke(message);

    // Verify state persists (read back)
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forKeyValueEntity(entityId)
                      .method(ProtobufCustomerKvEntity::get)
                      .invoke();

              assertThat(state.getName()).isEqualTo("Persistent Proto Name");
              assertThat(state.getEmail()).isEqualTo("from-protobuf@test.com");
            });
  }

  // ============================================================
  // Async component client calls with protobuf parameters
  // ============================================================

  @Test
  public void shouldPassProtobufMessageAsyncToKvEntity() {
    var entityId = "kv-proto-async-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder().setText("Async KV").setNumber(9999).setFlag(true).build();

    // Call entity method asynchronously
    var result =
        await(
            componentClient
                .forKeyValueEntity(entityId)
                .method(ProtobufCustomerKvEntity::echoProtobuf)
                .invokeAsync(message));

    assertThat(result.getText()).isEqualTo("Async KV");
    assertThat(result.getNumber()).isEqualTo(9999);
    assertThat(result.getFlag()).isTrue();
  }

  @Test
  public void shouldPassProtobufMessageAsyncToEsEntity() {
    var entityId = "es-proto-async-" + UUID.randomUUID();
    var message =
        SimpleMessage.newBuilder().setText("Async ES").setNumber(7777).setFlag(false).build();

    // Call entity method asynchronously
    var result =
        await(
            componentClient
                .forEventSourcedEntity(entityId)
                .method(ProtobufCustomerEntity::echoProtobuf)
                .invokeAsync(message));

    assertThat(result.getText()).isEqualTo("Async ES");
    assertThat(result.getNumber()).isEqualTo(7777);
    assertThat(result.getFlag()).isFalse();
  }

  // ============================================================
  // Timed action with protobuf parameters
  // ============================================================

  @Test
  public void shouldPassProtobufMessageToTimedAction() {
    var message =
        SimpleMessage.newBuilder().setText("hello from proto").setNumber(42).setFlag(true).build();

    timerScheduler.createSingleTimer(
        "proto-timed-action",
        ofMillis(0),
        componentClient
            .forTimedAction()
            .method(ProtoInputTimedAction::someMessage)
            .deferred(message));

    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var value = StaticTestBuffer.getValue("proto-timed-action");
              assertThat(value).isEqualTo("hello from proto:42:true");
            });
  }
}
