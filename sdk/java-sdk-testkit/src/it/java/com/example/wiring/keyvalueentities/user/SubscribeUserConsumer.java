/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.keyvalueentities.user;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.DeleteHandler;
import akka.platform.javasdk.consumer.Consumer;

@ComponentId("subscribe-user-action")
@Consume.FromKeyValueEntity(UserEntity.class)
public class SubscribeUserConsumer extends Consumer {

  public Effect onUpdate(User user) {
    String userId = messageContext().metadata().get("ce-subject").get();
    UserSideEffect.addUser(userId, user);
    return effects().ignore();
  }

  @DeleteHandler
  public Effect onDelete() {
    String userId = messageContext().metadata().get("ce-subject").get();
    UserSideEffect.removeUser(userId);
    return effects().ignore();
  }
}
