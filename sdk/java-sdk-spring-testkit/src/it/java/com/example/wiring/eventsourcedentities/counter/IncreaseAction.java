/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import kalix.javasdk.action.Action;
import kalix.javasdk.action.ActionCreationContext;
import kalix.javasdk.annotations.Consume;
import kalix.javasdk.client.ComponentClient;

import java.util.concurrent.CompletionStage;

public class IncreaseAction extends Action {

  private ComponentClient componentClient;

  private ActionCreationContext context;

  public IncreaseAction(ComponentClient componentClient, ActionCreationContext context) {
    this.componentClient = componentClient;
    this.context = context;
  }

  @Consume.FromEventSourcedEntity(value = CounterEntity.class)
  public Effect<CounterEvent.ValueMultiplied> printMultiply(CounterEvent.ValueMultiplied event) {
    return effects().reply(event);
  }

  @Consume.FromEventSourcedEntity(value = CounterEntity.class)
  public Effect<CounterEvent.ValueSet> printSet(CounterEvent.ValueSet event) {
    return effects().reply(event);
  }

  @Consume.FromEventSourcedEntity(value = CounterEntity.class)
  public Effect<Integer> printIncrease(CounterEvent.ValueIncreased event) {
    String entityId = this.actionContext().metadata().asCloudEvent().subject().get();
    if (event.value() == 42) {
      CompletionStage<Integer> res = componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invokeAsync(1);
      return effects().asyncReply(res);
    }
    return effects().reply(event.value());
  }
}
