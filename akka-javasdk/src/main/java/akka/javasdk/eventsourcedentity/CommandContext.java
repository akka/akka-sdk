/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

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
   *
   * @deprecated not used anymore
   */
  @Deprecated
  long commandId();

  /**
   * The id of the entity that this context is for.
   *
   * @return The entity id.
   */
  String entityId();

  boolean isDeleted();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
