/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import com.google.protobuf.GeneratedMessageV3;
import java.lang.annotation.*;

/**
 * Declares the concrete protobuf message classes used as events.
 *
 * <p>This annotation is only needed when using protobuf messages ({@link GeneratedMessageV3}
 * subclasses) as events. When using Java sealed interfaces for events, the event types are
 * automatically discovered from the permitted subclasses.
 *
 * <p>Can be applied to:
 *
 * <ul>
 *   <li><b>Event Sourced Entities</b> — declares the allowed event types. If the entity tries to
 *       persist an event type not listed, the operation fails. The {@code applyEvent} handler
 *       accepts {@link GeneratedMessageV3} and uses pattern matching to handle each type.
 *   <li><b>Consumers</b> — declares the protobuf event types the consumer handles. When a handler
 *       method accepts {@link GeneratedMessageV3}, the annotation tells the SDK which concrete
 *       types to of messages that are expected. If another type of message arrives to the consumer
 *       it will fail processing it. If the annotation is not present, the types are auto-resolved
 *       from the source entity's {@code @ProtoEventTypes} annotation if it is a local event sourced
 *       entity (using {@link Consume.FromEventSourcedEntity}.)
 *   <li><b>View TableUpdaters</b> — same as consumers: declares the protobuf event types the
 *       updater handles. If omitted, types are auto-resolved from the source entity.
 * </ul>
 *
 * <p>Example usage on an Event Sourced Entity:
 *
 * <pre>{@code
 * @ComponentId("my-entity")
 * @ProtoEventTypes({CustomerCreated.class, CustomerNameChanged.class})
 * public class MyEntity extends EventSourcedEntity<MyState, GeneratedMessageV3> {
 *   // ...
 * }
 * }</pre>
 *
 * <p>Example usage on a Consumer (explicit types):
 *
 * <pre>{@code
 * @ComponentId("my-consumer")
 * @Consume.FromEventSourcedEntity(MyEntity.class)
 * @ProtoEventTypes({CustomerCreated.class, CustomerNameChanged.class})
 * public class MyConsumer extends Consumer {
 *   public Effect onEvent(GeneratedMessageV3 event) { ... }
 * }
 * }</pre>
 *
 * <p>When consuming from an entity that already has {@code @ProtoEventTypes}, the annotation on the
 * consumer or view can be omitted and the types will be resolved from the source entity
 * automatically.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProtoEventTypes {
  /**
   * The protobuf message classes used as event types.
   *
   * @return array of protobuf message classes representing the event types
   */
  Class<? extends GeneratedMessageV3>[] value();
}
