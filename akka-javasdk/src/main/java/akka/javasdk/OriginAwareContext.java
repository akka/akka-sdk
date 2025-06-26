/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.Consume;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import java.util.Optional;

public interface OriginAwareContext extends Context {


  /**
   * When available, this method returns the region where a message was first created.
   *
   * <ul>
   *   <li>It returns a non-empty Optional when consuming events from an {@link EventSourcedEntity}
   *   or a change updates from a {@link KeyValueEntity}</li>
   *   <li>It returns an empty Optional when consuming messages from a topic (see {@link Consume.FromTopic})
   *   or from another service (see {@link Consume.FromServiceStream})</li>
   * </ul>
   *
   * @return the region where a message was first created. When not applicable, it returns an empty Optional.
   */
  Optional<String> originRegion();

  /**
   * Returns {@code true} if this message originated in the same region where it is currently being processed.
   * A message is considered to have originated in a region if it was created in that region.
   * In all other regions, the same message is treated as a replication.
   *
   * <p>For messages coming from Akka entities:
   *
   * <ul>
   *   <li>If the message is an event from an {@link EventSourcedEntity} or a change update from a
   *   {@link KeyValueEntity}, it returns {@code true} if it was originated in the region where this consumer is
   *   running. Otherwise, it returns {@code false}.
   *   <li>This method will always return {@code false} when consuming messages from another service
   *   (see {@link Consume.FromServiceStream}) or from a topic (see {@link Consume.FromTopic}).
   * </ul>
   *
   * @return {@code true} if the message originated in the current processing region, {@code false} otherwise
   */
  default boolean hasLocalOrigin() {
    // empty means we are consuming from a broker or service-to-service
    // for non-empty origins, it needs to match selfRegion
    // in dev-mode, both will be "" and therefore always considered as local
    return originRegion().stream().allMatch(orig -> orig.equals(selfRegion()));
  }

}
