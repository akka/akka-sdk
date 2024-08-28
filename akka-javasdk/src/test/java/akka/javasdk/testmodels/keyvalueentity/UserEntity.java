/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

@ComponentId("user")
public class UserEntity extends KeyValueEntity<User> {
  @Override
  public User emptyState() {
    return null;
  }

  public KeyValueEntity.Effect<Done> createUser(CreateUser createUser) {
    return effects().reply(Done.instance);
  }
}
