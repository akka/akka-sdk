/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.keyvalueentity;

import akka.javasdk.annotations.ComponentId;

@ComponentId("ve")
public class TestValueEntityMigration extends KeyValueEntity<TestVEState2> {

  public Effect<TestVEState2> get() {
    return effects().reply(currentState());
  }

}
