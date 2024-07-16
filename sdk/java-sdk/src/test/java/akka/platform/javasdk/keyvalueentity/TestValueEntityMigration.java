/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.javasdk.keyvalueentity;

import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("ve")
public class TestValueEntityMigration extends KeyValueEntity<TestVEState2> {

  public Effect<TestVEState2> get() {
    return effects().reply(currentState());
  }

}
