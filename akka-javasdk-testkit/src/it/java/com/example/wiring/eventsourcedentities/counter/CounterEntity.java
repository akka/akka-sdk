/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-entity")
public class CounterEntity extends EventSourcedEntity<Counter, CounterEvent> {


  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  public Effect<Integer> increase(Integer value) {
    logger.info(
      "Increasing counter with commandId={} commandName={} seqNr={} current={} value={}",
      commandContext().commandId(),
      commandContext().commandName(),
      commandContext().sequenceNumber(),
      currentState(),
      value);
    return effects().persist(new CounterEvent.ValueIncreased(value)).thenReply(Counter::value);
  }


  public Effect<Integer> set(Integer value) {
    return effects().persist(new CounterEvent.ValueSet(value)).thenReply(Counter::value);
  }

  public ReadOnlyEffect<Integer> get() {
    // don't modify, we want to make sure we call currentState().value here
    return effects().reply(currentState().value());
  }

  public Effect<Integer> times(Integer value) {
    logger.info(
        "Multiplying counter with commandId={} commandName={} seqNr={} current={} by value={}",
        commandContext().commandId(),
        commandContext().commandName(),
        commandContext().sequenceNumber(),
        currentState(),
        value);

    return effects().persist(new CounterEvent.ValueMultiplied(value)).thenReply(Counter::value);
  }

  public Effect<Integer> restart() { // force entity restart, useful for testing
    logger.info(
        "Restarting counter with commandId={} commandName={} seqNr={} current={}",
        commandContext().commandId(),
        commandContext().commandName(),
        commandContext().sequenceNumber(),
        currentState());

    throw new RuntimeException("Forceful restarting entity!");
  }

  @Override
  public Counter applyEvent(CounterEvent event) {
    return currentState().apply(event);
  }

}
