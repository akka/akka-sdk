/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.valueentity;

import akka.platform.javasdk.annotations.TypeId;

@TypeId("ve")
public class TestValueEntityMigration extends ValueEntity<TestVEState2> {

  public Effect<TestVEState2> get() {
    return effects().reply(currentState());
  }

}
