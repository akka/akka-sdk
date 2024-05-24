/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.tracingcounter;


import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.eventsourcedentity.EventSourcedEntityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TypeId("tcounter")
public class TCounterEntity extends EventSourcedEntity<TCounter, TCounterEvent> {

    Logger log = LoggerFactory.getLogger(TCounterEntity.class);

    private EventSourcedEntityContext context;

    public TCounterEntity(EventSourcedEntityContext context){
        this.context = context;
    }

    @Override
    public TCounter emptyState() {
        return new TCounter(0);
    }

    public Effect<Integer> increase(Integer value){
        log.info("increasing [{}].", value);
        return effects().emitEvent(new TCounterEvent.ValueIncreased(value)).thenReply(c -> c.count());
    }

    @EventHandler
    public TCounter handleIncrease(TCounterEvent.ValueIncreased increase){
        return currentState().onValueIncrease(increase.value());
    }
}
