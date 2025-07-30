/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.annotations.ComponentId;

@ComponentId("ve")
public class TestValueEntity extends KeyValueEntity<TestVEState1> {

  @Override
  public TestVEState1 emptyState() {
    return new TestVEState1("empty", 1);
  }

  public Effect<TestVEState1> get() {
    return effects().reply(currentState());
  }
}
