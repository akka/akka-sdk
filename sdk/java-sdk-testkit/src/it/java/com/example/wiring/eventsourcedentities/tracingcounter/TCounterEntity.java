/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.tracingcounter;


import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TypeId("tcounter")
public class TCounterEntity extends EventSourcedEntity<TCounter, TCounterEvent> {

    Logger log = LoggerFactory.getLogger(TCounterEntity.class);

    @Override
    public TCounter emptyState() {
        return new TCounter(0);
    }

    @Override
    public TCounter applyEvent(TCounterEvent event) {
        return switch (event) {
            case TCounterEvent.ValueIncreased evt -> currentState().onValueIncrease(evt.value());
        };
    }

    public Effect<Integer> increase(Integer value) {
        log.info("increasing [{}].", value);
        return effects().persist(new TCounterEvent.ValueIncreased(value)).thenReply(c -> c.count());
    }
}
