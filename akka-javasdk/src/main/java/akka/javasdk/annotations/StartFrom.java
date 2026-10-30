/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Where a {@link akka.javasdk.consumer.Consumer} starts when it is deployed for the first time.
 *
 * <p>Without this annotation a new consumer processes the whole history of its source. With {@code
 * latest} it starts from the time of its first deployment, with {@code timestamp} it starts from
 * the given point in time. Exactly one of the two must be set. Events and state changes from before
 * the start point are not consumed.
 *
 * <p>Supported for consumers of Event Sourced Entities, Key Value Entities, Workflows and service
 * to service event streams. Not supported for consumers of message broker topics.
 *
 * <p>The start point is recorded on the first deployment and is permanent. The consumer never
 * receives events from before it, also not when its stored offsets are cleared. A consumer that is
 * already deployed and gets this annotation skips the events from before the start point that it
 * has not yet processed.
 *
 * <p>The service refuses to start if the start point is removed or changed to another timestamp.
 * Changing a timestamp to {@code latest} is accepted and keeps the recorded timestamp. Changing
 * {@code latest} to a timestamp is refused. To start over from a different point, give the consumer
 * a new component id. That also discards its processing progress. A consumer that is added again
 * with an old component id reuses its recorded start point and stored offsets. To change a recorded
 * start point, contact support.
 *
 * <p>In development mode and in tests nothing is recorded. {@code latest} is then the time of each
 * start of the service, and events that are written while the service is stopped are not delivered.
 * Messages that a test publishes through the test kit do not go through the start point. A
 * timestamp in the future delivers nothing until that time.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StartFrom {

  /** Start from the time of the first deployment of the consumer. */
  boolean latest() default false;

  /** Start from a point in time, an ISO-8601 instant such as {@code "2026-10-01T00:00:00Z"}. */
  String timestamp() default "";
}
