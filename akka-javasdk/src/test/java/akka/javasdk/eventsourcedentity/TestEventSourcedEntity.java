/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.eventsourcedentity;

import akka.javasdk.annotations.ComponentId;

@ComponentId("es")
public class TestEventSourcedEntity extends EventSourcedEntity<TestESState, TestESEvent> {

  @Override
  public TestESState emptyState() {
    return new TestESState("", 0, false, "");
  }

  public ReadOnlyEffect<TestESState> get() {
    return effects().reply(currentState());
  }

  @Override
  public TestESState applyEvent(TestESEvent event) {
    return switch (event) {
      case TestESEvent.Event1 event1 ->
          new TestESState(
              event1.s(), currentState().i(), currentState().b(), currentState().anotherString());
      case TestESEvent.Event2 event2 ->
          new TestESState(
              currentState().s(),
              event2.newName(),
              currentState().b(),
              currentState().anotherString());
      case TestESEvent.Event3 event3 ->
          new TestESState(
              currentState().s(), currentState().i(), event3.b(), currentState().anotherString());
      case TestESEvent.Event4 event4 ->
          new TestESState(
              currentState().s(), currentState().i(), currentState().b(), event4.anotherString());
      default -> throw new IllegalStateException("Unexpected value: " + event);
    };
  }
}
