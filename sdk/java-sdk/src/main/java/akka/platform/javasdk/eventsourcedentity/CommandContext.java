/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.eventsourcedentity;

import akka.platform.javasdk.MetadataContext;

/** An event sourced command context. */
public interface CommandContext extends MetadataContext {
  /**
   * The current sequence number of events in this entity.
   *
   * @return The current sequence number.
   */
  long sequenceNumber();

  /**
   * The name of the command being executed.
   *
   * @return The name of the command.
   */
  String commandName();

  /**
   * The id of the command being executed.
   *
   * @return The id of the command.
   */
  long commandId();

  /**
   * The id of the entity that this context is for.
   *
   * @return The entity id.
   */
  String entityId();
}
