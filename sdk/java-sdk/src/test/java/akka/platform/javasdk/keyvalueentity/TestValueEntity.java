/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import akka.platform.javasdk.annotations.TypeId;

@TypeId("ve")
public class TestValueEntity extends KeyValueEntity<TestVEState1> {

  @Override
  public TestVEState1 emptyState() {
    return new TestVEState1("empty", 1);
  }

  public Effect<TestVEState1> get() {
    return effects().reply(currentState());
  }

}
