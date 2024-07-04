/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.platform.javasdk.valueentity.ValueEntity;

/**
 * Not for user extension
 */
@DoNotInherit
public interface ValueEntityClient {

  /**
   * Pass in a Value Entity command handler method reference, e.g. <code>UserEntity::create</code>
   */
  <T, R> ComponentMethodRef<R> method(Function<T, ValueEntity.Effect<R>> methodRef);

  /**
   * Pass in a Value Entity command handler method reference, e.g. <code>UserEntity::update</code>
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, ValueEntity.Effect<R>> methodRef);


}
