/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

public class ValueEntitiesTestModels {

  @Component(id = "user")
  public static class InvalidValueEntityWithOverloadedCommandHandler extends KeyValueEntity<User> {
    public KeyValueEntity.Effect<Done> createEntity(CreateUser createUser) {
      return effects().reply(Done.instance);
    }

    public KeyValueEntity.Effect<Done> createEntity(String user) {
      return effects().reply(Done.instance);
    }
  }
}
