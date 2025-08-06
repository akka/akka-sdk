/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import com.google.common.collect.HashMultimap;
import java.util.List;

public class CounterEventSourcedEntity extends EventSourcedEntity<Integer, CounterEvent> {

  public record SomeRecord(String text) {}

  public Effect<String> increaseBy(Integer value) {
    if (value <= 0) return effects().error("Can't increase with a negative value");
    else if (wouldOverflow(value))
      return effects().error("Can't increase by [" + value + "] due to overflow");
    else
      return effects()
          .persist(new CounterEvent.Increased(commandContext().entityId(), value))
          .thenReply(__ -> "Ok");
  }

  public Effect<Response> commandHandlerWithResponse() {
    return effects().reply(new Response.Error());
  }

  public Effect<String> commandHandlerWithPolyInput(Response response) {
    return effects().reply("Ok");
  }

  public Effect<String> set(Integer value) {
    var map = HashMultimap.<String, String>create();
    return effects()
        .persist(new CounterEvent.Set(commandContext().entityId(), value, map))
        .thenReply(__ -> "Ok");
  }

  public Effect<String> increaseFromMeta() {
    return effects()
        .persist(
            new CounterEvent.Increased(
                commandContext().entityId(),
                Integer.parseInt(commandContext().metadata().get("value").get())))
        .thenReply(__ -> "Ok");
  }

  public Effect<String> doubleIncreaseBy(Integer value) {
    if (value < 0) return effects().error("Can't increase with a negative value");
    else if (wouldOverflow(value + value) || (value + value) < 0) {
      return effects().error("Can't double-increase by [" + value + "] due to overflow");
    } else {
      CounterEvent.Increased event = new CounterEvent.Increased(commandContext().entityId(), value);
      return effects().persist(event, event).thenReply(__ -> "Ok");
    }
  }

  public Effect<String> delete() {
    return effects()
        .persist(new CounterEvent.Increased(commandContext().entityId(), 0))
        .deleteEntity()
        .thenReply(__ -> "Ok");
  }

  public ReadOnlyEffect<List<SomeRecord>> returnList() {
    return effects().reply(List.of(new SomeRecord("ok")));
  }

  @Override
  public Integer applyEvent(CounterEvent counterEvent) {
    return switch (counterEvent) {
      case CounterEvent.Increased increased -> {
        if (currentState() == null) yield increased.value();
        else yield currentState() + increased.value();
      }
      case CounterEvent.Set set -> set.value();
    };
  }

  private boolean wouldOverflow(Integer increase) {
    var current = currentState();

    return (current != null) && (increase > (Integer.MAX_VALUE - current));
  }
}
