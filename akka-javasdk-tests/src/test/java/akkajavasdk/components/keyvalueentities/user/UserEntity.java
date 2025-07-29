/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.user;

import akka.javasdk.CommandException;
import akkajavasdk.Ok;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akkajavasdk.components.MyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@ComponentId("user")
public class UserEntity extends KeyValueEntity<User> {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public record CreatedUser(String name, String email) {};
  public record UpdateEmail(String newEmail) {};
  public record Delete() {};
  public record Restart() {};


  public ReadOnlyEffect<User> getUser() {
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

  public Effect<Boolean> nameIsLikeOneOf(List<String> names) {
    return effects().reply(currentState() != null && names.stream().anyMatch(n -> n.equals(currentState().name)));
  }

  public Effect<Boolean> nameIsLikeOneOfUsers(List<User> users) {
    return effects().reply(currentState() != null && users.stream().anyMatch(c -> c.name.equals(currentState().name)));
  }

  public Effect<Ok> deleteUser(Delete cmd) {
    logger.info(
        "Deleting user with commandId={} commandName={} current={}",
        commandContext().commandId(),
        commandContext().commandName(),
        currentState());
    return effects().deleteEntity().thenReply(Ok.instance);
  }

  public Effect<Boolean> getDelete() {
    return effects().reply(isDeleted());
  }

  public Effect<Integer> restart(Restart cmd) { // force entity restart, useful for testing
    logger.info(
        "Restarting counter with commandId={} commandName={} current={}",
        commandContext().commandId(),
        commandContext().commandName(),
        currentState());

    throw new RuntimeException("Forceful restarting entity!");
  }

  public Effect<String> run(String errorType) {
    if ("errorMessage".equals(errorType)) {
      return effects().error(errorType);
    } else if ("errorCommandException".equals(errorType)) {
      return effects().error(new CommandException(errorType));
    } else if ("errorMyException".equals(errorType)) {
      return effects().error(new MyException(errorType, new MyException.SomeData("some data")));
    } else if ("throwMyException".equals(errorType)) {
      throw new MyException(errorType, new MyException.SomeData("some data"));
    } else if ("throwRuntimeException".equals(errorType)) {
      throw new RuntimeException(errorType);
    } else {
      return effects().reply("No error triggered for: " + errorType);
    }
  }
}
