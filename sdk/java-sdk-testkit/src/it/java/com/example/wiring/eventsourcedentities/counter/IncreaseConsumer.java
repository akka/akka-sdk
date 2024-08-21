/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import akka.Done;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.consumer.Consumer;
import akka.platform.javasdk.consumer.ConsumerContext;

import java.util.concurrent.CompletionStage;

@ComponentId("increase-action")
@Consume.FromEventSourcedEntity(value = CounterEntity.class)
public class IncreaseConsumer extends Consumer {

  private ComponentClient componentClient;

  private ConsumerContext context;

  public IncreaseConsumer(ComponentClient componentClient, ConsumerContext context) {
    this.componentClient = componentClient;
    this.context = context;
  }

  public Effect printMultiply(CounterEvent.ValueMultiplied event) {
    return effects().done();
  }

  public Effect printSet(CounterEvent.ValueSet event) {
    return effects().done();
  }

  public Effect printIncrease(CounterEvent.ValueIncreased event) {
    String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
    if (event.value() == 42) {
      CompletionStage<Done> res = componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invokeAsync(1).thenApply(__ -> Done.getInstance());
      return effects().acyncDone(res);
    }
    return effects().done();
  }
}
