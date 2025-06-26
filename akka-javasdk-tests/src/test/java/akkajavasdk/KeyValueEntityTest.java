/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity;
import akkajavasdk.components.keyvalueentities.hierarchy.AbstractTextConsumer;
import akkajavasdk.components.keyvalueentities.hierarchy.TextKvEntity;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.HashSet;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(Junit5LogCapturing.class)
public class KeyValueEntityTest extends TestKitSupport {

  @Test
  public void verifyValueEntityCurrentState() {

    var joe1 = new TestUser("veUser1", "john@doe.com", "veJane");
    createUser(joe1);

    var newEmail = joe1.email() + "2";
    // change email uses the currentState internally
    changeEmail(joe1.withEmail(newEmail));

    Assertions.assertEquals(newEmail, getUser(joe1).email);
  }

  @Test
  public void verifyValueEntityCurrentStateAfterRestart() {

    var joe2 = new TestUser("veUser2", "veJane@doe.com", "veJane");
    createUser(joe2);

    restartUserEntity(joe2);

    var newEmail = joe2.email() + "2";

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
  public void testHierarchyEntity() {
    var client = componentClient.forKeyValueEntity("some-id");

    client.method(TextKvEntity::setText).invoke("my text");

    var result = client.method(TextKvEntity::getText).invoke();
    assertThat(result).isEqualTo(Optional.of("my text"));

    // also verify that hierarchy consumer works
    Awaitility.await().untilAsserted(() ->
        org.assertj.core.api.Assertions.assertThat(StaticTestBuffer.getValue(AbstractTextConsumer.BUFFER_KEY)).isEqualTo("my text")
    );
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
                  .forKeyValueEntity(generic.id())
                  .method(UserEntity::nameIsLikeOneOf)
                  .invokeAsync(List.of(generic.name())));

          Assertions.assertTrue(found);
        });

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          // change email uses the currentState internally
          var found = await(
              componentClient
                  .forKeyValueEntity(generic.id())
                  .method(UserEntity::nameIsLikeOneOfUsers)
                  .invokeAsync(List.of(new User(generic.name(), generic.email()))));

          Assertions.assertTrue(found);
        });

  }

  @Test
  public void verifyListOfRecordsReturn() {
    var found = await(
        componentClient
            .forKeyValueEntity("listofrecords")
            .method(CustomerEntity::returnAListOfRecords)
            .invokeAsync());
    assertThat(found.size()).isEqualTo(2);
    assertThat(new HashSet<>(found)).isEqualTo(Set.of(new CustomerEntity.SomeRecord("text1", 1), new CustomerEntity.SomeRecord("text2", 2)));
  }

  @Test
  public void verifyOptionalRecordReturn() {
    var found = await(
        componentClient
            .forKeyValueEntity("listofrecords")
            .method(CustomerEntity::returnOptionalRecord)
            .invokeAsync());
    assertThat(found).isEqualTo(Optional.of(new CustomerEntity.SomeRecord("text1", 1)));
  }

  private void createUser(TestUser user) {
    await(
      componentClient
        .forKeyValueEntity(user.id())
        .method(UserEntity::createUser)
        .invokeAsync(new UserEntity.CreatedUser(user.name(), user.email())));
  }

  private void changeEmail(TestUser user) {
    await(
      componentClient
        .forKeyValueEntity(user.id())
        .method(UserEntity::updateEmail)
        .invokeAsync(new UserEntity.UpdateEmail(user.email())));
  }

  private User getUser(TestUser user) {
    return await(
      componentClient
        .forKeyValueEntity(user.id())
        .method(UserEntity::getUser)
        .invokeAsync());
  }

  private void restartUserEntity(TestUser user) {
    try {
      await(
        componentClient
          .forKeyValueEntity(user.id())
          .method(UserEntity::restart)
          .invokeAsync(new UserEntity.Restart()));

      fail("This should not be reached");
    } catch (Exception ignored) {
    }
  }
}
