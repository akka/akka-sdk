/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import akka.javasdk.Metadata;
import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.client.NoEntryFoundException;
import akka.javasdk.testkit.KalixTestKit;
import akka.javasdk.testkit.KalixIntegrationTestKitSupport;
import com.example.wiring.actions.echo.ActionWithMetadata;
import com.example.wiring.actions.echo.EchoAction;
import com.example.wiring.actions.echo.Message;
import com.example.wiring.actions.headers.TestBuffer;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.headers.ForwardHeadersESEntity;
import com.example.wiring.keyvalueentities.customer.CustomerEntity;
import com.example.wiring.keyvalueentities.headers.ForwardHeadersValueEntity;
import com.example.wiring.keyvalueentities.user.AssignedCounterEntity;
import com.example.wiring.keyvalueentities.user.User;
import com.example.wiring.keyvalueentities.user.UserEntity;
import com.example.wiring.keyvalueentities.user.UserSideEffect;
import com.example.wiring.views.CountersByValue;
import com.example.wiring.views.CountersByValueSubscriptions;
import com.example.wiring.views.CountersByValueWithIgnore;
import com.example.wiring.views.CustomerByCreationTime;
import com.example.wiring.views.UserCounter;
import com.example.wiring.views.UserCounters;
import com.example.wiring.views.UserCountersView;
import com.example.wiring.views.UserWithVersion;
import com.example.wiring.views.UserWithVersionView;
import com.example.wiring.views.UsersByEmailAndName;
import com.example.wiring.views.UsersByName;
import com.example.wiring.views.UsersView;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class SdkIntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(10, SECONDS);

  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    // here only to show how to set different `Settings` in a test.
    return KalixTestKit.Settings.DEFAULT
      .withAclEnabled()
      .withAdvancedViews()
      .withWorkflowTickInterval(ofMillis(500))
      .withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }


  @Test
  public void verifyEchoActionWiring() {

    timerScheduler.startSingleTimer("echo-action", ofMillis(0), componentClient.forTimedAction()
      .method(EchoAction::stringMessage)
      .deferred("hello"));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = TestBuffer.getValue("echo-action");
        assertThat(value).isEqualTo("hello");
      });
  }

  @Test
  public void verifyCounterEventSourceSubscription() {
    // GIVEN IncreaseAction is subscribed to CounterEntity events
    // WHEN the CounterEntity is requested to increase 42\
    String entityId = "hello1";
    await(
      componentClient.forEventSourcedEntity(entityId)
        .method(CounterEntity::increase)
        .invokeAsync(42));

    // THEN IncreaseAction receives the event 42 and increases the counter 1 more
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer result = await(
          componentClient.forEventSourcedEntity(entityId)
            .method(CounterEntity::get).invokeAsync());

        assertThat(result).isEqualTo(43); //42 +1
      });
  }

  @Test
  public void verifyActionIsNotSubscribedToMultiplyAndRouterIgnores() {
    var entityId = "counterId2";
    EventSourcedEntityClient counterClient = componentClient.forEventSourcedEntity(entityId);
    await(counterClient.method(CounterEntity::increase).invokeAsync(1));
    await(counterClient.method(CounterEntity::times).invokeAsync(2));
    Integer lastKnownValue = await(counterClient.method(CounterEntity::increase).invokeAsync(1234));

    assertThat(lastKnownValue).isEqualTo(1 * 2 + 1234);

    //Once the action IncreaseActionWithIgnore processes event 1234 it adds 1 more to the counter
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        Integer result = await(
          componentClient.forEventSourcedEntity(entityId)
            .method(CounterEntity::get).invokeAsync());

        assertThat(result).isEqualTo(1 * 2 + 1234 + 1);
      });
  }

  @Test
  public void verifyViewIsNotSubscribedToMultiplyAndRouterIgnores() {

    var entityId = "counterId4";
    EventSourcedEntityClient counterClient = componentClient.forEventSourcedEntity(entityId);
    await(counterClient.method(CounterEntity::increase).invokeAsync(1));
    await(counterClient.method(CounterEntity::times).invokeAsync(2));
    Integer counterGet = await(counterClient.method(CounterEntity::increase).invokeAsync(1));

    assertThat(counterGet).isEqualTo(1 * 2 + 1);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(
        () -> {
          var byValue = await(
            componentClient.forView()
              .method(CountersByValueWithIgnore::getCounterByValue)
              .invokeAsync(CountersByValueWithIgnore.queryParam(2)));

          assertThat(byValue.value()).isEqualTo(1 + 1);
        });
  }

  @Test
  public void verifyFindCounterByValue() {

    await(
      componentClient.forEventSourcedEntity("abc")
        .method(CounterEntity::increase)
        .invokeAsync(10));


    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .untilAsserted(
        () -> {
          var byValue = await(
            componentClient.forView()
              .method(CountersByValue::getCounterByValue)
              .invokeAsync(CountersByValue.queryParam(10)));

          assertThat(byValue.value()).isEqualTo(10);
        });
  }

  @Test
  public void verifyCounterViewMultipleSubscriptions() {

    await(
      componentClient.forEventSourcedEntity("hello2")
        .method(CounterEntity::increase)
        .invokeAsync(1));

    await(
      componentClient.forEventSourcedEntity("hello3")
        .method(CounterEntity::increase)
        .invokeAsync(1));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(
        () ->
          await(componentClient.forView()
            .method(CountersByValueSubscriptions::getCounterByValue)
            .invokeAsync(new CountersByValueSubscriptions.QueryParameters(1)))
            .counters().size(),
        new IsEqual<>(2));
  }

  @Test
  public void verifyTransformedUserViewWiring() {

    TestUser user = new TestUser("123", "john@doe.com", "JohnDoe");

    createUser(user);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> getUserByEmail(user.email).version,
        new IsEqual(1));

    updateUser(user.withName("JohnDoeJr"));

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> getUserByEmail(user.email).version,
        new IsEqual(2));
  }

  @Test
  public void verifyUserSubscriptionAction() {

    TestUser user = new TestUser("123", "john@doe.com", "JohnDoe");

    createUser(user);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> UserSideEffect.getUsers().get(user.id),
        new IsEqual(new User(user.name, user.email)));

    deleteUser(user);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> UserSideEffect.getUsers().get(user.id),
        new IsNull<>());
  }


  @Test
  public void shouldDeleteValueEntityAndDeleteViewsState() {

    TestUser user = new TestUser("userId", "john2@doe.com", "Bob");
    createUser(user);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> getUserByEmail(user.email).version,
        new IsEqual(1));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> getUsersByName(user.name).size(),
        new IsEqual(1));

    deleteUser(user);

    Awaitility.await()
      .atMost(15, TimeUnit.of(SECONDS))
      .ignoreExceptions()
      .untilAsserted(
        () -> {
          var ex =
            failed(
              componentClient.forView()
                .method(UserWithVersionView::getUser)
                .invokeAsync(UserWithVersionView.queryParam(user.email)));
          // FIXME why is the root ex java.util.concurrent.ExecutionException and not our exception
          Assertions.assertTrue(ex.getCause() instanceof NoEntryFoundException);
        });

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> getUsersByName(user.name).size(),
        new IsEqual(0));
  }

  @Test
  public void verifyFindUsersByEmail() {

    TestUser user = new TestUser("JohnDoe", "john3@doe.com", "JohnDoe");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(
        () -> {
          var byEmail = await(
            componentClient.forView()
              .method(UsersView::getUserByEmail)
              .invokeAsync(UsersView.byEmailParam(user.email)));

          assertThat(byEmail.email).isEqualTo(user.email);
        });
  }

  @Test
  public void verifyFindUsersByName() {

    TestUser user = new TestUser("JohnDoe2", "john4@doe.com", "JohnDoe2");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(
        () -> {
          var byName = getUsersByName(user.name).getFirst();
          assertThat(byName.name).isEqualTo(user.name);
        });
  }

  @Test
  public void verifyFindUsersByEmailAndName() {

    TestUser user = new TestUser("JohnDoe2", "john3@doe.com2", "JohnDoe2");
    createUser(user);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(
        () -> {
          var request = new UsersByEmailAndName.QueryParameters(user.email, user.name);

          var byEmail =
            await(
              componentClient.forView()
                .method(UsersByEmailAndName::getUsers)
                .invokeAsync(request));

          assertThat(byEmail.email).isEqualTo(user.email);
          assertThat(byEmail.name).isEqualTo(user.name);
        });
  }

  @Test
  public void verifyMultiTableViewForUserCounters() {

    TestUser alice = new TestUser("alice", "alice@foo.com", "Alice Foo");
    TestUser bob = new TestUser("bob", "bob@bar.com", "Bob Bar");

    createUser(alice);
    createUser(bob);

    assignCounter("c1", alice.id);
    assignCounter("c2", bob.id);
    assignCounter("c3", alice.id);
    assignCounter("c4", bob.id);

    increaseCounter("c1", 11);
    increaseCounter("c2", 22);
    increaseCounter("c3", 33);
    increaseCounter("c4", 44);

    // the view is eventually updated

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> getUserCounters(alice.id).counters.size(), new IsEqual<>(2));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> getUserCounters(bob.id).counters.size(), new IsEqual<>(2));

    UserCounters aliceCounters = getUserCounters(alice.id);
    assertThat(aliceCounters.id).isEqualTo(alice.id);
    assertThat(aliceCounters.email).isEqualTo(alice.email);
    assertThat(aliceCounters.name).isEqualTo(alice.name);
    assertThat(aliceCounters.counters).containsOnly(new UserCounter("c1", 11), new UserCounter("c3", 33));

    UserCounters bobCounters = getUserCounters(bob.id);

    assertThat(bobCounters.id).isEqualTo(bob.id);
    assertThat(bobCounters.email).isEqualTo(bob.email);
    assertThat(bobCounters.name).isEqualTo(bob.name);
    assertThat(bobCounters.counters).containsOnly(new UserCounter("c2", 22), new UserCounter("c4", 44));
  }

  @Test
  public void verifyActionWithMetadata() {

    String metadataValue = "action-value";
    String veHeaderValue = "ve-value";
    String esHeaderValue = "es-value";

    timerScheduler.startSingleTimer("metadata", ofMillis(0), componentClient.forTimedAction()
      .method(ActionWithMetadata::processWithMeta)
      .withMetadata(Metadata.EMPTY.add(ActionWithMetadata.SOME_HEADER, metadataValue))
      .deferred());

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .ignoreExceptions()
      .untilAsserted(() -> {
        var header = TestBuffer.getValue(ActionWithMetadata.SOME_HEADER);
        assertThat(header).isEqualTo(metadataValue);
      });

    Message veResponse =
      await(
        componentClient.forKeyValueEntity("1")
          .method(ForwardHeadersValueEntity::createUser)
          .withMetadata(Metadata.EMPTY.add(ForwardHeadersValueEntity.SOME_HEADER, veHeaderValue))
          .invokeAsync()
      );

    assertThat(veResponse.text()).isEqualTo(veHeaderValue);

    Message esResponse =
      await(
        componentClient.forEventSourcedEntity("1")
          .method(ForwardHeadersESEntity::createUser)
          .withMetadata(Metadata.EMPTY.add(ForwardHeadersESEntity.SOME_HEADER, esHeaderValue))
          .invokeAsync()
      );

    assertThat(esResponse.text()).isEqualTo(esHeaderValue);
  }

  @Test
  public void searchWithInstant() {

    var now = Instant.now().truncatedTo(SECONDS);
    createCustomer(new CustomerEntity.Customer("customer1", now));

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> getCustomersByCreationDate(now).size(), new IsEqual(1));

    var later = now.plusSeconds(60 * 5);
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> getCustomersByCreationDate(later).size(), new IsEqual(0));
  }


  @NotNull
  private List<User> getUsersByName(String name) {
    return await(
      componentClient.forView()
        .method(UsersByName::getUsers)
        .invokeAsync(new UsersByName.QueryParameters(name)))
      .users();
  }

  @Nullable
  private UserWithVersion getUserByEmail(String email) {
    return await(
      componentClient.forView()
        .method(UserWithVersionView::getUser)
        .invokeAsync(UserWithVersionView.queryParam(email)));
  }

  private void updateUser(TestUser user) {
    Ok userUpdate =
      await(
        componentClient.forKeyValueEntity(user.id)
          .method(UserEntity::createOrUpdateUser)
          .invokeAsync(new UserEntity.CreatedUser(user.name, user.email)));

    assertThat(userUpdate).isEqualTo(Ok.instance);
  }

  private void createUser(TestUser user) {
    Ok userCreation =
      await(
        componentClient.forKeyValueEntity(user.id)
          .method(UserEntity::createOrUpdateUser)
          .invokeAsync(new UserEntity.CreatedUser(user.name, user.email)));
    assertThat(userCreation).isEqualTo(Ok.instance);
  }


  private void createCustomer(CustomerEntity.Customer customer) {

    Ok created =
      await(
        componentClient
          .forKeyValueEntity(customer.name())
          .method(CustomerEntity::create)
          .invokeAsync(customer));

    assertThat(created).isEqualTo(Ok.instance);
  }


  @NotNull
  private List<CustomerEntity.Customer> getCustomersByCreationDate(Instant createdOn) {
    return await(
      componentClient.forView()
        .method(CustomerByCreationTime::getCustomerByTime)
        .invokeAsync(new CustomerByCreationTime.QueryParameters(createdOn)))
      .customers();
  }


  private void deleteUser(TestUser user) {
    Ok userDeleted =
      await(
        componentClient
          .forKeyValueEntity(user.id)
          .method(UserEntity::deleteUser)
          .invokeAsync(new UserEntity.Delete()));
    assertThat(userDeleted).isEqualTo(Ok.instance);
  }

  private void increaseCounter(String id, int value) {
    await(
      componentClient.forEventSourcedEntity(id)
        .method(CounterEntity::increase)
        .invokeAsync(value));
  }

  private void multiplyCounter(String id, int value) {
    await(
      componentClient.forEventSourcedEntity(id)
        .method(CounterEntity::times)
        .invokeAsync(value));
  }

  private void assignCounter(String id, String assignee) {
    await(
      componentClient.forKeyValueEntity(id)
        .method(AssignedCounterEntity::assign)
        .invokeAsync(assignee));
  }

  private UserCounters getUserCounters(String userId) {
    return await(
      componentClient.forView().method(UserCountersView::get)
        .invokeAsync(UserCountersView.queryParam(userId)));
  }
}

class TestUser {
  public final String id;
  public final String email;
  public final String name;

  public TestUser(String id, String email, String name) {
    this.id = id;
    this.email = email;
    this.name = name;
  }

  public TestUser withName(String newName) {
    return new TestUser(id, email, newName);
  }

  public TestUser withEmail(String newEmail) {
    return new TestUser(id, newEmail, name);
  }
}
