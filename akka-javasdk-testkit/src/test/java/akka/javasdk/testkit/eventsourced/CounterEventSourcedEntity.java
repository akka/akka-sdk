/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.eventsourcedentity.EventSourcedEntity;

public class CounterEventSourcedEntity extends EventSourcedEntity<Integer, Increased> {

  public Effect<String> increaseBy(Integer value) {
    if (value <= 0) return effects().error("Can't increase with a negative value");
    else if (wouldOverflow(value)) return effects().error("Can't increase by [" + value + "] due to overflow");
    else return effects().persist(new Increased(commandContext().entityId(), value)).thenReply(__ -> "Ok");
  }

  public Effect<String> increaseFromMeta() {
    return effects().persist(new Increased(commandContext().entityId(), Integer.parseInt(commandContext().metadata().get("value").get()))).thenReply(__ -> "Ok");
  }

  public Effect<String> doubleIncreaseBy(Integer value) {
    if (value < 0) return effects().error("Can't increase with a negative value");
    else if (wouldOverflow(value + value)) {
      return effects().error("Can't double-increase by [" + value + "] due to overflow");
    } else {
      Increased event = new Increased(commandContext().entityId(), value);
      return effects().persist(event, event).thenReply(__ -> "Ok");
    }
  }

  @Override
  public Integer applyEvent(Increased increased) {
    if (currentState() == null) return increased.value();
    else return currentState() + increased.value();
  }

  private boolean wouldOverflow(Integer increase) {
    var current = currentState();

    if (current == null) return false;
    else return (increase > (Integer.MAX_VALUE - currentState()));
  }
}
