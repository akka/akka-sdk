/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.example.wiring.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.function.Function.identity;

@ComponentId("counter-entity")
public class CounterEntity extends EventSourcedEntity<Counter, CounterEvent> {

  public enum Error{
    TOO_HIGH, TOO_LOW
  }

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

  public Effect<Result<Error, Counter>> increaseWithResult(Integer value) {
    if (value <= 0){
      return effects().reply(new Result.Error<>(CounterEntity.Error.TOO_LOW));
    } else if (value > 10000) {
      return effects().reply(new Result.Error<>(CounterEntity.Error.TOO_HIGH));
    }else {
      return effects()
        .persist(new CounterEvent.ValueIncreased(value))
        .thenReply(Result.Success::new);
    }
  }

  @Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET)) //required for testing
  public Effect<Counter> increaseWithError(Integer value) {
    if (value <= 0){
      return effects().error("Value must be greater than 0");
    } else if (value > 10000) {
      return effects().error("Value must be less than 10000");
    }else {
      return effects()
        .persist(new CounterEvent.ValueIncreased(value))
        .thenReply(identity());
    }
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
