/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.valueentity;

import akka.platform.javasdk.annotations.TypeId;
import akka.platform.javasdk.valueentity.ValueEntity;
import akka.platform.spring.testmodels.Done;

@TypeId("user")
public class UserEntity extends ValueEntity<User> {
  @Override
  public User emptyState() {
    return null;
  }

  public ValueEntity.Effect<Done> createUser(CreateUser createUser) {
    return effects().reply(Done.instance);
  }
}
