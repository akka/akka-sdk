/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.action.ActionContext;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;

import java.util.concurrent.CompletionStage;

@ComponentId("increase-action")
public class IncreaseAction extends Action {

  private ComponentClient componentClient;

  private ActionContext context;

  public IncreaseAction(ComponentClient componentClient, ActionContext context) {
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
    String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
    if (event.value() == 42) {
      CompletionStage<Integer> res = componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invokeAsync(1);
      return effects().asyncReply(res);
    }
    return effects().reply(event.value());
  }
}
