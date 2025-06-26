/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.hierarchy;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public abstract class AbstractTextEsEntity<E> extends EventSourcedEntity<AbstractTextEsEntity.State, E> {

  public record State(String value) {}

}
