/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;

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
