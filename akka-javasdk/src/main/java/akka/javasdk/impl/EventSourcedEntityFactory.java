/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl;

import akka.annotation.InternalApi;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import akka.javasdk.impl.eventsourcedentity.EventSourcedEntityRouter;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

/**
 * Low level interface for handling events and commands on an entity.
 *
 * <p>Generally, this should not be needed, instead, a class extending a generated abstract {@link
 * EventSourcedEntity} should be used.
 *
 * <p>INTERNAL API
 */
@InternalApi
public interface EventSourcedEntityFactory {
  /**
   * Create an entity handler for the given context.
   *
   * @param context The context.
   * @return The handler for the given context.
   */
  EventSourcedEntityRouter<?, ?, ?> create(EventSourcedEntityContext context);
}
