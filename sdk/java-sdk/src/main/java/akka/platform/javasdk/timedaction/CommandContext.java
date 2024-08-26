/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.timedaction;

import akka.platform.javasdk.MetadataContext;
import io.opentelemetry.api.trace.Tracer;

/** Context for action calls. */
public interface CommandContext extends MetadataContext {

  /**
   * Get an OpenTelemetry tracer for the current action. This will allow for building and automatic
   * exporting of spans.
   *
   * @return A tracer for the current action, if tracing is configured. Otherwise, a noops tracer.
   */
  Tracer getTracer();
}
