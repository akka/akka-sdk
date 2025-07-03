/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

/**
 * Context information available to Key Value Entity command handlers during command processing.
 * Provides access to command metadata, entity identification, and tracing capabilities.
 *
 * <p>This context is automatically provided by the Akka runtime and can be accessed within command
 * handlers using {@link KeyValueEntity#commandContext()}.
 */
public interface CommandContext extends MetadataContext {

  /**
   * Returns the name of the command currently being executed. This corresponds to the method name
   * of the command handler being invoked.
   *
   * @return the name of the command being processed
   */
  String commandName();

  /**
   * Returns the command id for the current command.
   *
   * @return the command id
   * @deprecated This method is no longer used and will be removed in a future version
   */
  @Deprecated
  long commandId();

  /**
   * Returns the unique identifier of the entity instance that this command is being executed on.
   * This is the same id used when calling the entity through a component client.
   *
   * @return the unique entity id for this entity instance
   */
  String entityId();

  /**
   * Provides access to tracing functionality for adding custom application-specific tracing
   * information to the current command processing.
   *
   * @return the tracing context for custom tracing operations
   */
  Tracing tracing();
}
