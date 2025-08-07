/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.javasdk.MetadataContext;
import akka.javasdk.Tracing;

/** Context for action calls. */
public interface CommandContext extends MetadataContext {

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();
}
