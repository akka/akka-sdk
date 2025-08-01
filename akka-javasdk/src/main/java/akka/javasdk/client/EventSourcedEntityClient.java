/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;

/** Not for user extension */
@DoNotInherit
public interface EventSourcedEntityClient {

  /**
   * Pass in an Event Sourced Entity command handler method reference, e.g. {@code
   * UserEntity::create}
   */
  <T, R> ComponentMethodRef<R> method(Function<T, EventSourcedEntity.Effect<R>> methodRef);

  /**
   * Pass in an Event Sourced Entity command handler method reference, e.g. {@code
   * UserEntity::update}
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(
      Function2<T, A1, EventSourcedEntity.Effect<R>> methodRef);
}
