/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

/** Context for an event. */
public interface EventContext extends EventSourcedEntityContext {
  /**
   * The sequence number of the current event being processed.
   *
   * @return The sequence number.
   */
  long sequenceNumber();
}
