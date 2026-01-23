/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link akka.javasdk.consumer.Consumer} or {@link akka.javasdk.view.View} can use this
 * annotation on a method to define that it accepts snapshots from the {@link
 * akka.javasdk.eventsourcedentity.EventSourcedEntity} source.
 *
 * <p>Using snapshots can be a performance improvement over consuming all events, especially for a
 * new consumer or view when there is a long event history that must be processed to catch up to the
 * latest state.
 *
 * <p>It can only be used together with {link {@link Consume.FromEventSourcedEntity} and for a
 * service-to-service consumer it is only defined on the producer side, which will transform the
 * snapshot to a public event. The consumer side will process the snapshot event like any other
 * event.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SnapshotHandler {}
