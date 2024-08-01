/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.action;

import akka.platform.javasdk.CloudEvent;
import akka.platform.javasdk.MetadataContext;
import io.opentelemetry.api.trace.Tracer;

import java.util.Optional;

/** Context for action calls. */
public interface MessageContext extends MetadataContext {

  /**
   * The origin subject of the {@link CloudEvent}. For example, the entity id when the event was
   * emitted from an entity.
   */
  Optional<String> eventSubject();

  /**
   * Get an OpenTelemetry tracer for the current action. This will allow for building and automatic
   * exporting of spans.
   *
   * @return A tracer for the current action, if tracing is configured. Otherwise, a noops tracer.
   */
  Tracer getTracer();
}
