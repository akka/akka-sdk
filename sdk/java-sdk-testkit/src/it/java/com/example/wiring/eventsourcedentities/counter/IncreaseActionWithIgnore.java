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

@ComponentId("increase-action-with-ignore")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true)
public class IncreaseActionWithIgnore extends Action {

    private ComponentClient componentClient;

    private ActionContext context;

    public IncreaseActionWithIgnore(ComponentClient componentClient, ActionContext context) {
        this.componentClient = componentClient;
        this.context = context;
    }

    public Effect<Integer> oneShallPass(CounterEvent.ValueIncreased event) {
        String entityId = this.messageContext().metadata().asCloudEvent().subject().get();
        if (event.value() == 1234) {
            CompletionStage<Integer> res =
                componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invokeAsync(1);
            return effects().asyncReply(res);
        }
        return effects().reply(event.value());
    }
}