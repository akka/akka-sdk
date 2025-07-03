/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

/**
 * Context information available to Event Sourced Entity command handlers during command processing.
 * Provides access to command metadata, entity identification, sequence information, and tracing capabilities.
 * 
 * <p>This context is automatically provided by the Akka runtime and can be accessed within
 * command handlers using {@link EventSourcedEntity#commandContext()}.
 */
public interface CommandContext extends MetadataContext {
  /**
   * Returns the current sequence number of events in this entity. This represents the number
   * of events that have been persisted for this entity instance.
   *
   * @return the current sequence number of persisted events
   */
  long sequenceNumber();

  /**
   * Returns the name of the command currently being executed. This corresponds to the
   * method name of the command handler being invoked.
   *
   * @return the name of the command being processed
   */
  String commandName();

  /**
   * Returns the command ID for the current command.
   *
   * @return the command ID
   * @deprecated This method is no longer used and will be removed in a future version
   */
  @Deprecated
  long commandId();

  /**
   * Returns the unique identifier of the entity instance that this command is being executed on.
   * This is the same ID used when calling the entity through a component client.
   *
   * @return the unique entity ID for this entity instance
   */
  String entityId();

  /**
   * Returns whether this entity has been marked for deletion.
   *
   * @return {@code true} if the entity has been deleted, {@code false} otherwise
   */
  boolean isDeleted();

  /**
   * Provides access to tracing functionality for adding custom application-specific tracing
   * information to the current command processing.
   * 
   * @return the tracing context for custom tracing operations
   */
  Tracing tracing();
}
