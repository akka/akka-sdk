/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.javasdk.CloudEvent;
import akka.javasdk.MetadataContext;
import akka.javasdk.OriginAwareContext;

import java.util.Optional;

/** Context for view update calls. */
public interface UpdateContext extends MetadataContext, OriginAwareContext {

  /**
   * The origin subject of the {@link CloudEvent}. For example, the entity id when the event was
   * emitted from an entity.
   */
  Optional<String> eventSubject();

  // FIXME is this needed anymore?
  /** The name of the event being handled. */
  String eventName();
}
