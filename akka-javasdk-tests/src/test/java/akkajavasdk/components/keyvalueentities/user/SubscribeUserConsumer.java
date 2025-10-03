/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;

@Component(id = "subscribe-user-action")
@Consume.FromKeyValueEntity(UserEntity.class)
public class SubscribeUserConsumer extends Consumer {

  public Effect onUpdate(User user) {
    String userId = messageContext().metadata().get("ce-subject").get();
    UserSideEffect.addUser(userId, user);

    if (messageContext().metadata().has(CounterEntity.META_KEY)) {
      UserSideEffect.setMeta(
          userId, messageContext().metadata().getLast(UserEntity.META_KEY).get());
    }

    return effects().ignore();
  }

  @DeleteHandler
  public Effect onDelete() {
    String userId = messageContext().metadata().get("ce-subject").get();
    UserSideEffect.removeUser(userId);
    return effects().ignore();
  }
}
