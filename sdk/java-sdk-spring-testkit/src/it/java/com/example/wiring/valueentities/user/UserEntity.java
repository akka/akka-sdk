/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.valueentities.user;

import com.example.wiring.Ok;
import kalix.javasdk.StatusCode;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TypeId("user")
public class UserEntity extends ValueEntity<User> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public record CreatedUser(String name, String email) {};
  public record UpdateEmail(String newEmail) {};
  public record Delete() {};
  public record Restart() {};


  public Effect<User> getUser() {
    if (currentState() == null)
      return effects().error("User not found");

    return effects().reply(currentState());
  }

  public Effect<Ok> createOrUpdateUser(CreatedUser createdUser) {
    return effects().updateState(new User(createdUser.name, createdUser.email)).thenReply(Ok.instance);
  }

  public Effect<Ok> createUser(CreatedUser createdUser) {
    return effects().updateState(new User(createdUser.name, createdUser.email)).thenReply(Ok.instance);
  }

  public Effect<Ok> updateEmail(UpdateEmail cmd) {
    return effects().updateState(new User(currentState().name, cmd.newEmail)).thenReply(Ok.instance);
  }

  public Effect<Ok> deleteUser(Delete cmd) {
    return effects().deleteEntity().thenReply(Ok.instance);
  }

  public Effect<Integer> restart(Restart cmd) { // force entity restart, useful for testing
    logger.info(
        "Restarting counter with commandId={} commandName={} current={}",
        commandContext().commandId(),
        commandContext().commandName(),
        currentState());

    throw new RuntimeException("Forceful restarting entity!");
  }
}
