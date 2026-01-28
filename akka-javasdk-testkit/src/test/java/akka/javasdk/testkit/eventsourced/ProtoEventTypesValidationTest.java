/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

/**
 * Tests for @ProtoEventTypes validation in the EventSourcedTestKit. These tests verify that the
 * testkit correctly validates event types when @ProtoEventTypes is used on an entity.
 */
public class ProtoEventTypesValidationTest {

  @Test
  public void shouldAllowPersistingDeclaredEventType() {
    EventSourcedTestKit<String, ?, ProtoEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new ProtoEventSourcedEntity());

    EventSourcedResult<String> result =
        testKit.method(ProtoEventSourcedEntity::persistAllowedEvent).invoke("test-value");

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("ok");
    assertThat(testKit.getState()).isEqualTo("test-value");
  }

  @Test
  public void shouldAllowPersistingAnotherDeclaredEventType() {
    EventSourcedTestKit<String, ?, ProtoEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new ProtoEventSourcedEntity());

    EventSourcedResult<String> result =
        testKit.method(ProtoEventSourcedEntity::persistAllowedEventDuration).invoke(42L);

    assertThat(result.isReply()).isTrue();
    assertThat(result.getReply()).isEqualTo("ok");
    assertThat(testKit.getState()).isEqualTo("42");
  }

  @Test
  public void shouldRejectEventTypeNotInProtoEventTypesAnnotation() {
    EventSourcedTestKit<String, ?, ProtoEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new ProtoEventSourcedEntity());

    assertThatThrownBy(
            () -> testKit.method(ProtoEventSourcedEntity::persistNotAllowedEvent).invoke(123L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not declared in @ProtoEventTypes")
        .hasMessageContaining("Timestamp")
        .hasMessageContaining("StringValue")
        .hasMessageContaining("Duration");
  }
}
