/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;

/**
 * Not for user extension
 */
@DoNotInherit
public interface KeyValueEntityClient {

  /**
   * Pass in a Key Value Entity command handler method reference, e.g. <code>UserEntity::create</code>
   */
  <T, R> ComponentMethodRef<R> method(Function<T, KeyValueEntity.Effect<R>> methodRef);

  /**
   * Pass in a Key Value Entity command handler method reference, e.g. <code>UserEntity::update</code>
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, KeyValueEntity.Effect<R>> methodRef);


}
