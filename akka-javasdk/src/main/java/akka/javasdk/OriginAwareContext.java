/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk;

import akka.javasdk.annotations.Consume;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.workflow.Workflow;
import java.util.Optional;

public interface OriginAwareContext extends Context {

  /**
   * When available, this method returns the region where a message was first created.
   *
   * <ul>
   *   <li>It returns a non-empty Optional when consuming events from an {@link EventSourcedEntity},
   *       a change updates from a {@link KeyValueEntity} or a {@link Workflow}
   *   <li>It returns an empty Optional when consuming messages from a topic (see {@link
   *       Consume.FromTopic}) or from another service (see {@link Consume.FromServiceStream})
   * </ul>
   *
   * @return the region where a message was first created. When not applicable, it returns an empty
   *     Optional.
   */
  Optional<String> originRegion();

  /**
   * Returns {@code true} if this message originated in the current processing region, and for
   * messages consumed from a topic or another service.
   *
   * <p>The result depends on the message source:
   *
   * <ul>
   *   <li>If the message originates from an {@link EventSourcedEntity}, {@link KeyValueEntity}, or
   *       {@link Workflow} in the same region as this consumer is running, it returns {@code true}.
   *   <li>When consuming messages from another service (see {@link Consume.FromServiceStream}) or
   *       from a topic (see {@link Consume.FromTopic}), it returns {@code true}. This does not
   *       establish where the message was created or prevent processing in multiple regions.
   * </ul>
   *
   * @return {@code true} if the message originated from a component in the current processing
   *     region, from a broker or from service-to-service eventing, {@code false} otherwise.
   */
  default boolean hasLocalOrigin() {
    // empty means we are consuming from a broker or service-to-service
    // for non-empty origins, it needs to match selfRegion
    // in dev-mode, both will be "" and therefore always considered as local
    return originRegion().stream().allMatch(orig -> orig.equals(selfRegion()));
  }
}
