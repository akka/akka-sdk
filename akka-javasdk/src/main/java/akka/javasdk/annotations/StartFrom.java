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
 * <p>The start point is recorded on the first deployment and must not be changed or removed after
 * that, the service refuses to start if it is. To start over from a different point, give the
 * consumer a new component id. That also discards its processing progress.
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
