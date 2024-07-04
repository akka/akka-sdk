/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.valueentities.user;

import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;

public class SubscribeUserAction extends Action {

  @Consume.FromValueEntity(UserEntity.class)
  public Action.Effect<String> onUpdate(User user) {
    String userId = actionContext().metadata().get("ce-subject").get();
    UserSideEffect.addUser(userId, user);
    return effects().ignore();
  }

  @Consume.FromValueEntity(value = UserEntity.class, handleDeletes = true)
  public Action.Effect<String> onDelete() {
    String userId = actionContext().metadata().get("ce-subject").get();
    UserSideEffect.removeUser(userId);
    return effects().ignore();
  }
}
