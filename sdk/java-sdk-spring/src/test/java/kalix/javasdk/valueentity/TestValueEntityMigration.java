/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.javasdk.valueentity;

import kalix.javasdk.annotations.TypeId;

@TypeId("ve")
public class TestValueEntityMigration extends ValueEntity<TestVEState2> {

  public Effect<TestVEState2> get() {
    return effects().reply(currentState());
  }

}
