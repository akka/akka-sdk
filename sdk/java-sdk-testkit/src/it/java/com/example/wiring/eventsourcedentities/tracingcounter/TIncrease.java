/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.tracingcounter;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import akka.platform.javasdk.consumer.ConsumerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ComponentId("t-increase-action")
@Consume.FromEventSourcedEntity(value = TCounterEntity.class)
public class TIncrease extends Consumer {

    Logger log = LoggerFactory.getLogger(TIncrease.class);

    private ConsumerContext actionContext;

    public TIncrease(ConsumerContext actionContext){
        this.actionContext = actionContext;
    }

    public Effect printIncrease(TCounterEvent.ValueIncreased increase){
        log.info("increasing [{}].", increase);
        return effects().done();
    }



}
