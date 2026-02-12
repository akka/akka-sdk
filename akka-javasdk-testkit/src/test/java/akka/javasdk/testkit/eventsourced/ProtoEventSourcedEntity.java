/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ProtoEventTypes;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.google.protobuf.Duration;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.StringValue;
import com.google.protobuf.Timestamp;

/**
 * Event sourced entity with @ProtoEventTypes for testing validation in the testkit. Uses standard
 * Google protobuf types so no custom proto files are needed.
 */
@Component(id = "test-proto-entity")
@ProtoEventTypes({StringValue.class, Duration.class})
public class ProtoEventSourcedEntity extends EventSourcedEntity<String, GeneratedMessageV3> {

  @Override
  public String emptyState() {
    return "";
  }

  @Override
  public String applyEvent(GeneratedMessageV3 event) {
    return switch (event) {
      case StringValue sv -> sv.getValue();
      case Duration d -> String.valueOf(d.getSeconds());
      default -> throw new IllegalArgumentException("Unknown event type: " + event.getClass());
    };
  }

  /** Command handler that persists an allowed event type (StringValue). */
  public Effect<String> persistAllowedEvent(String value) {
    return effects().persist(StringValue.of(value)).thenReply(__ -> "ok");
  }

  /** Command handler that persists another allowed event type (Duration). */
  public Effect<String> persistAllowedEventDuration(long seconds) {
    return effects()
        .persist(Duration.newBuilder().setSeconds(seconds).build())
        .thenReply(__ -> "ok");
  }

  /**
   * Command handler that tries to persist an event type NOT in @ProtoEventTypes. Timestamp is NOT
   * in the annotation, so this should fail with validation error.
   */
  public Effect<String> persistNotAllowedEvent(long seconds) {
    return effects()
        .persist(Timestamp.newBuilder().setSeconds(seconds).build())
        .thenReply(__ -> "should not reach here");
  }
}
