/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package kalix.spring.testmodels.valueentity;

import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import kalix.spring.testmodels.Done;

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
