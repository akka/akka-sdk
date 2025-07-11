/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

/** A value based workflow command context. */
public interface CommandContext extends MetadataContext {

  /**
   * The name of the command being executed.
   *
   * @return The name of the command.
   */
  String commandName();

  /**
   * The id of the command being executed.
   *
   * @deprecated not used anymore
   */
  @Deprecated
  long commandId();

  /**
   * The id of the workflow that this context is for.
   *
   * @return The workflow id.
   */
  String workflowId();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
