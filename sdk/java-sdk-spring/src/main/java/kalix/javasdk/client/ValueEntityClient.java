/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import kalix.javasdk.Metadata;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.spring.impl.KalixClient;

import java.util.Optional;

public class ValueEntityClient {

  private final KalixClient kalixClient;
  private final Optional<Metadata> callMetadata;
  private final String entityId;


  public ValueEntityClient(KalixClient kalixClient, Optional<Metadata> callMetadata, String entityId) {
    this.kalixClient = kalixClient;
    this.callMetadata = callMetadata;
    this.entityId = entityId;
  }

  /**
   * Pass in a Value Entity method reference annotated as a REST endpoint, e.g. <code>UserEntity::create</code>
   */
  public <T, R> ComponentMethodRef<R> method(Function<T, ValueEntity.Effect<R>> methodRef) {
    return new ComponentMethodRef<>(kalixClient, methodRef, Optional.of(entityId), callMetadata);
  }

  /**
   * Pass in a Value Entity method reference annotated as a REST endpoint, e.g. <code>UserEntity::create</code>
   */
  public <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, ValueEntity.Effect<R>> methodRef) {
    return new ComponentMethodRef1<>(kalixClient, methodRef, Optional.of(entityId), callMetadata);
  }

}
