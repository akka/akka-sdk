/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.client;

import akka.japi.function.Function;
import akka.japi.function.Function2;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.Metadata;
import kalix.javasdk.action.Action;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.spring.impl.KalixClient;

import java.util.List;
import java.util.Optional;

public class EventSourcedEntityClient {

  private final KalixClient kalixClient;
  private final Optional<Metadata> callMetadata;
  private final String entityId;


  public EventSourcedEntityClient(KalixClient kalixClient, Optional<Metadata> callMetadata, String entityId) {
    this.kalixClient = kalixClient;
    this.callMetadata = callMetadata;
    this.entityId = entityId;
  }

  /**
   * Pass in an Event Sourced Entity method reference, e.g. <code>UserEntity::create</code>
   */
  public <T, R> DeferredCall<Any, R> call(Function<T, EventSourcedEntity.Effect<R>> methodRef) {
    return ComponentCall.noParams(kalixClient, methodRef, List.of(entityId), callMetadata);
  }
  /**
   * Pass in an Event Sourced Entity method reference, e.g. <code>UserEntity::create</code>
   */
  public <T, A1, R> ComponentCall<A1, R> call(Function2<T, A1, EventSourcedEntity.Effect<R>> methodRef) {
    return new ComponentCall<>(kalixClient, methodRef, List.of(entityId), callMetadata);
  }


}
