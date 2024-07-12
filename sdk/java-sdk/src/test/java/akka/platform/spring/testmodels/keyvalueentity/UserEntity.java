/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.keyvalueentity;

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.spring.testmodels.Done;

@TypeId("user")
public class UserEntity extends KeyValueEntity<User> {
  @Override
  public User emptyState() {
    return null;
  }

  public KeyValueEntity.Effect<Done> createUser(CreateUser createUser) {
    return effects().reply(Done.instance);
  }
}
