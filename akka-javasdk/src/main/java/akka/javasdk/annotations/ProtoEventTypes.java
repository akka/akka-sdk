/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import com.google.protobuf.GeneratedMessageV3;
import java.lang.annotation.*;

/**
 * Declares the protobuf message classes that are used as events for an Event Sourced Entity.
 *
 * <p>This annotation is only needed when using protobuf messages ({@link GeneratedMessageV3}
 * subclasses) as events. When using Java sealed interfaces for events, the event types are
 * automatically discovered from the permitted subclasses.
 *
 * <p>If the entity tries to persist a type of event not listed in the entity, the operation is
 * failed.
 *
 * <p>The event handler of such an Event Sourced Entity needs to accept {@link GeneratedMessageV3}
 * and it is up to user logic to match the expected types.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @Component(id = "my-entity")
 * @ProtoEventType({CustomerCreated.class, CustomerNameChanged.class, CustomerDeleted.class})
 * public class MyEntity extends EventSourcedEntity<MyState, GeneratedMessageV3> {
 *   // ...
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ProtoEventTypes {
  /**
   * The protobuf message classes that are used as events for this Event Sourced Entity.
   *
   * @return array of protobuf message classes representing the event types
   */
  Class<? extends GeneratedMessageV3>[] value();
}
