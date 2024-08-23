/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.keyvalueentities.user;

import akka.Done;
import akka.http.javadsl.model.StatusCodes;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.consumer.ConsumerContext;

public class ValidateUserAction extends Action {

  private ConsumerContext ctx;
  private ComponentClient componentClient;

  public ValidateUserAction(ConsumerContext ctx, ComponentClient componentClient) {
    this.ctx = ctx;
    this.componentClient = componentClient;
  }

  public record CreateUser(String user, String email, String name){}
  public Action.Effect createOrUpdateUser(CreateUser createUser) {
    if (createUser.email.isEmpty() || createUser.name.isEmpty())
      return effects().error("No field can be empty");

    var reply =
      componentClient
        .forKeyValueEntity(createUser.user)
        .method(UserEntity::createUser)
        .invokeAsync(new UserEntity.CreatedUser(createUser.name, createUser.email));
    return effects().done();
  }

  public record UpdateEmail(String user, String email){}
  public Action.Effect updateEmail(UpdateEmail updateEmail) {
    if (updateEmail.email.isEmpty())
      return effects().error("No field can be empty");

    return effects().asyncDone(      componentClient
      .forKeyValueEntity(updateEmail.user)
      .method(UserEntity::updateEmail)
      .invokeAsync(new UserEntity.UpdateEmail(updateEmail.email))
      .thenApply(__ -> Done.done()));
  }

  public Action.Effect delete(String user) {
    return effects().asyncDone(componentClient
      .forKeyValueEntity(user)
      .method(UserEntity::deleteUser)
      .invokeAsync(new UserEntity.Delete())
      .thenApply(__ -> Done.done()));
  }
}
