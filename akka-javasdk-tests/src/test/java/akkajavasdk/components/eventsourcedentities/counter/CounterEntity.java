/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

import static akka.Done.done;
import static java.util.function.Function.identity;

import akka.Done;
import akka.javasdk.CommandException;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akkajavasdk.Result;
import akkajavasdk.components.MyException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-entity")
public class CounterEntity extends EventSourcedEntity<Counter, CounterEvent> {

  public enum Error {
    TOO_HIGH,
    TOO_LOW
  }

  private Integer errorCounter = 0;
  private final Logger logger = LoggerFactory.getLogger(getClass());

  public record DoIncrease(int amount) {}

  @Override
  public Counter emptyState() {
    return new Counter(0);
  }

  public Effect<Integer> increase(Integer value) {
    logger.info(
        "Increasing counter with commandName={} seqNr={} current={} value={}",
        commandContext().commandName(),
        commandContext().sequenceNumber(),
        currentState(),
        value);
    return effects().persist(new CounterEvent.ValueIncreased(value)).thenReply(Counter::value);
  }

  public Effect<Integer> failedIncrease(Integer value) {
    logger.info("Calling failedIncrease with value={}, errorCounter={}", value, errorCounter);
    if (errorCounter <= 2) {
      errorCounter++;
      return effects().error("simulated failure");
    } else {
      return effects().persist(new CounterEvent.ValueIncreased(value)).thenReply(Counter::value);
    }
  }

  public Effect<Result<Error, Counter>> increaseWithResult(Integer value) {
    if (value <= 0) {
      return effects().reply(new Result.Error<>(CounterEntity.Error.TOO_LOW));
    } else if (value > 10000) {
      return effects().reply(new Result.Error<>(CounterEntity.Error.TOO_HIGH));
    } else {
      return effects()
          .persist(new CounterEvent.ValueIncreased(value))
          .thenReply(Result.Success::new);
    }
  }

  public Effect<Counter> increaseWithError(Integer value) {
    if (value <= 0) {
      return effects().error("Value must be greater than 0");
    } else if (value > 10000) {
      return effects().error("Value must be less than 10000");
    } else {
      return effects().persist(new CounterEvent.ValueIncreased(value)).thenReply(identity());
    }
  }

  public ReadOnlyEffect<Boolean> commandHandlerIsOnVirtualThread() {
    return effects().reply(Thread.currentThread().isVirtual());
  }

  public Effect<Integer> set(Integer value) {
    return effects().persist(new CounterEvent.ValueSet(value)).thenReply(Counter::value);
  }

  public Effect<Integer> handle(CounterCommand counterCommand) {
    return switch (counterCommand) {
      case CounterCommand.Increase(var value) ->
          effects().persist(new CounterEvent.ValueIncreased(value)).thenReply(Counter::value);

      case CounterCommand.Set(var value) ->
          effects().persist(new CounterEvent.ValueSet(value)).thenReply(Counter::value);
    };
  }

  public Effect<Integer> multiIncrease(List<Integer> increase) {
    return effects()
        .persistAll(increase.stream().map(CounterEvent.ValueIncreased::new).toList())
        .thenReply(Counter::value);
  }

  public Effect<Integer> multiIncreaseCommands(List<DoIncrease> increase) {
    return effects()
        .persistAll(
            increase.stream().map(di -> new CounterEvent.ValueIncreased(di.amount)).toList())
        .thenReply(Counter::value);
  }

  public ReadOnlyEffect<Integer> get() {
    // don't modify, we want to make sure we call currentState().value here
    return effects().reply(currentState().value());
  }

  public ReadOnlyEffect<Boolean> getDeleted() {
    // don't modify, we want to make sure we call currentState().value here
    return effects().reply(isDeleted());
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

  public Effect<Done> delete() {
    return effects().persist(new CounterEvent.ValueSet(0)).deleteEntity().thenReply(__ -> done());
  }

  public Effect<String> run(String errorType) {
    if ("errorMessage".equals(errorType)) {
      return effects().error(errorType);
    } else if ("errorCommandException".equals(errorType)) {
      return effects().error(new CommandException(errorType));
    } else if ("errorMyException".equals(errorType)) {
      return effects().error(new MyException(errorType, new MyException.SomeData("some data")));
    } else if ("throwMyException".equals(errorType)) {
      throw new MyException(errorType, new MyException.SomeData("some data"));
    } else if ("throwRuntimeException".equals(errorType)) {
      throw new RuntimeException(errorType);
    } else {
      return effects().reply("No error triggered for: " + errorType);
    }
  }

  @Override
  public Counter applyEvent(CounterEvent event) {
    return currentState().apply(event);
  }
}
