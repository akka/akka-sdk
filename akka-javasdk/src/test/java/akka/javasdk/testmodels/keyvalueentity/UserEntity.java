/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.testmodels.Done;

@Component(id = "user")
public class UserEntity extends KeyValueEntity<User> {
  @Override
  public User emptyState() {
    return null;
  }

  public KeyValueEntity.Effect<Done> createUser(CreateUser createUser) {
    return effects().reply(Done.instance);
  }
}
