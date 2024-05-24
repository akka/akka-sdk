/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.eventsourcedentity;

import kalix.javasdk.annotations.EventHandler;
import kalix.javasdk.annotations.TypeId;

@TypeId("es")
public class TestEventSourcedEntity extends EventSourcedEntity<TestESState, TestESEvent> {


  @Override
  public TestESState emptyState() {
    return new TestESState("", 0, false, "");
  }

  // TODO: support parameterless methods
  public Effect<TestESState> get() {
    return effects().reply(currentState());
  }

  @EventHandler
  public TestESState apply(TestESEvent.Event1 event1) {
    return new TestESState(event1.s(), currentState().i(), currentState().b(), currentState().anotherString());
  }

  @EventHandler
  public TestESState apply(TestESEvent.Event2 event2) {
    return new TestESState(currentState().s(), event2.newName(), currentState().b(), currentState().anotherString());
  }

  @EventHandler
  public TestESState apply(TestESEvent.Event3 event3) {
    return new TestESState(currentState().s(), currentState().i(), event3.b(), currentState().anotherString());
  }

  @EventHandler
  public TestESState apply(TestESEvent.Event4 event4) {
    return new TestESState(currentState().s(), currentState().i(), currentState().b(), event4.anotherString());
  }
}
