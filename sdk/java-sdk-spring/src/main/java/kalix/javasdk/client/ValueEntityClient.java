/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function10;
import akka.japi.function.Function11;
import akka.japi.function.Function12;
import akka.japi.function.Function13;
import akka.japi.function.Function14;
import akka.japi.function.Function15;
import akka.japi.function.Function16;
import akka.japi.function.Function17;
import akka.japi.function.Function18;
import akka.japi.function.Function19;
import akka.japi.function.Function2;
import akka.japi.function.Function20;
import akka.japi.function.Function21;
import akka.japi.function.Function22;
import akka.japi.function.Function3;
import akka.japi.function.Function4;
import akka.japi.function.Function5;
import akka.japi.function.Function6;
import akka.japi.function.Function7;
import akka.japi.function.Function8;
import akka.japi.function.Function9;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.spring.impl.KalixClient;

import java.util.List;
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
  public <T, R> DeferredCall<Any, R> call(Function<T, ValueEntity.Effect<R>> methodRef) {
    return ComponentCall.noParams(kalixClient, methodRef, List.of(entityId), callMetadata);
  }

  /**
   * Pass in a Value Entity method reference annotated as a REST endpoint, e.g. <code>UserEntity::create</code>
   */
  public <T, A1, R> ComponentCall<A1, R> call(Function2<T, A1, ValueEntity.Effect<R>> methodRef) {
    return new ComponentCall<>(kalixClient, methodRef,  List.of(entityId), callMetadata);
  }

}
