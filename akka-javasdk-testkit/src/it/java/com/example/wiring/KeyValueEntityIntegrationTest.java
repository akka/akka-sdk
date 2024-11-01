/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import akka.javasdk.testkit.TestKitSupport;
import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.fail;

public class KeyValueEntityIntegrationTest extends TestKitSupport {

  @Test
  public void verifyValueEntityCurrentState() {

    var joe1 = new TestUser("veUser1", "john@doe.com", "veJane");
    createUser(joe1);

    var newEmail = joe1.email + "2";
    // change email uses the currentState internally
    changeEmail(joe1.withEmail(newEmail));

    Assertions.assertEquals(newEmail, getUser(joe1).email);
  }

  @Test
  public void verifyValueEntityCurrentStateAfterRestart() {

    var joe2 = new TestUser("veUser2", "veJane@doe.com", "veJane");
    createUser(joe2);

    restartUserEntity(joe2);

    var newEmail = joe2.email + "2";

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        // change email uses the currentState internally
        changeEmail(joe2.withEmail(newEmail));

        Assertions.assertEquals(newEmail, getUser(joe2).email);
      });
  }

  @Test
  public void verifyGenericParameter() {
    var generic = new TestUser("mrgeneric", "generic@example.com", "Generic");
    createUser(generic);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          // change email uses the currentState internally
          var found = await(
              componentClient
                  .forKeyValueEntity(generic.id)
                  .method(UserEntity::nameIsLikeOneOf)
                  .invokeAsync(List.of(generic.name)));

          Assertions.assertTrue(found);
        });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          // change email uses the currentState internally
          var found = await(
              componentClient
                  .forKeyValueEntity(generic.id)
                  .method(UserEntity::nameIsLikeOneOfUsers)
                  .invokeAsync(List.of(new User(generic.name, generic.email))));

          Assertions.assertTrue(found);
        });

  }

  private void createUser(TestUser user) {
    await(
      componentClient
        .forKeyValueEntity(user.id)
        .method(UserEntity::createUser)
        .invokeAsync(new UserEntity.CreatedUser(user.name, user.email)));
  }

  private void changeEmail(TestUser user) {
    await(
      componentClient
        .forKeyValueEntity(user.id)
        .method(UserEntity::updateEmail)
        .invokeAsync(new UserEntity.UpdateEmail(user.email)));
  }

  private User getUser(TestUser user) {
    return await(
      componentClient
        .forKeyValueEntity(user.id)
        .method(UserEntity::getUser)
        .invokeAsync());
  }

  private void restartUserEntity(TestUser user) {
    try {
      await(
        componentClient
          .forKeyValueEntity(user.id)
          .method(UserEntity::restart)
          .invokeAsync(new UserEntity.Restart()));

      fail("This should not be reached");
    } catch (Exception ignored) {
    }
  }
}
