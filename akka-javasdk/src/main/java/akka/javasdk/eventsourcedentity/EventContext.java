/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

/**
 * Context information available when processing events in the {@link EventSourcedEntity#applyEvent} method.
 * Provides access to event metadata and sequence information.
 * 
 * <p>This context is automatically provided by the Akka runtime and can be accessed within
 * the {@link EventSourcedEntity#applyEvent} method using {@link EventSourcedEntity#eventContext()}.
 */
public interface EventContext extends EventSourcedEntityContext {
  /**
   * Returns the sequence number of the current event being processed. This represents the
   * position of this event in the entity's event journal.
   *
   * @return the sequence number of the current event
   */
  long sequenceNumber();
}
