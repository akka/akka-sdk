/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.tracingcounter;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ComponentId("t-increase-action")
@Consume.FromEventSourcedEntity(value = TCounterEntity.class)
public class TIncrease extends Consumer {

    Logger log = LoggerFactory.getLogger(TIncrease.class);

    public Effect printIncrease(TCounterEvent.ValueIncreased increase){
        log.info("increasing [{}].", increase);
        return effects().done();
    }



}
