/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.consumer.ConsumerContext;

import java.util.concurrent.CompletionStage;

@ComponentId("increase-action-with-ignore")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
public class IncreaseActionWithIgnore extends Consumer {

    private ComponentClient componentClient;

    private ConsumerContext context;

    public IncreaseActionWithIgnore(ComponentClient componentClient, ConsumerContext context) {
        this.componentClient = componentClient;
        this.context = context;
    }

    public Effect oneShallPass(CounterEvent.ValueIncreased event) {
        String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
        if (event.value() == 1234) {
            CompletionStage<Done> res =
                componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invokeAsync(1)
                  .thenApply(__ -> Done.done());
            return effects().acyncDone(res);
        }
        return effects().done();
    }
}