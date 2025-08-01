/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.keyvalueentity.KeyValueEntity;

/** Not for user extension */
@DoNotInherit
public interface KeyValueEntityClient {

  /**
   * Pass in a Key Value Entity command handler method reference, e.g. {@code UserEntity::create}
   */
  <T, R> ComponentMethodRef<R> method(Function<T, KeyValueEntity.Effect<R>> methodRef);

  /**
   * Pass in a Key Value Entity command handler method reference, e.g. {@code UserEntity::update}
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(
      Function2<T, A1, KeyValueEntity.Effect<R>> methodRef);
}
