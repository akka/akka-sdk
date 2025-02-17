/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.consumer;

import akka.javasdk.CloudEvent;
import akka.javasdk.MetadataContext;
import akka.javasdk.OriginAwareContext;
import akka.javasdk.Tracing;

import java.util.Optional;

/** Context for an incoming message. */
public interface MessageContext extends MetadataContext, OriginAwareContext {

  /**
   * The origin subject of the {@link CloudEvent}. For example, the entity id when the event was
   * emitted from an entity.
   */
  Optional<String> eventSubject();

  /** Access to tracing for custom app specific tracing. */
  Tracing tracing();

}
