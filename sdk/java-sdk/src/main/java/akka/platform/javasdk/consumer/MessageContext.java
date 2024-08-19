/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.consumer;

import akka.platform.javasdk.CloudEvent;
import akka.platform.javasdk.MetadataContext;
import io.opentelemetry.api.trace.Tracer;

import java.util.Optional;

/** Context for an incoming message. */
public interface MessageContext extends MetadataContext {

  /**
   * The origin subject of the {@link CloudEvent}. For example, the entity id when the event was
   * emitted from an entity.
   */
  Optional<String> eventSubject();

  /**
   * Get an OpenTelemetry tracer for the current message. This will allow for building and automatic
   * exporting of spans.
   *
   * @return A tracer for the current message, if tracing is configured. Otherwise, a noops tracer.
   */
  Tracer getTracer();
}
