/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.javasdk.Metadata;
import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.actions.echo.ActionWithMetadata;
import akkajavasdk.components.actions.echo.EchoAction;
import akkajavasdk.components.actions.hierarchy.HierarchyTimed;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity;
import akkajavasdk.components.keyvalueentities.user.ProdCounterEntity;
import akkajavasdk.components.keyvalueentities.user.StageCounterEntity;
import akkajavasdk.components.keyvalueentities.user.TestCounterEntity;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserEntity;
import akkajavasdk.components.keyvalueentities.user.UserSideEffect;
import akkajavasdk.components.views.counter.CountersByValue;
import akkajavasdk.components.views.customer.CustomerByCreationTime;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static akkajavasdk.components.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(Junit5LogCapturing.class)
public class SdkIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    // here only to show how to set different `Settings` in a test.
    return TestKit.Settings.DEFAULT
      .withTopicOutgoingMessages(CUSTOMERS_TOPIC)
      //one defined here and one is the Setup class
      .withDisabledComponents(Set.of(StageCounterEntity.class));
  }

  @Test
  public void verifyIfComponentIsActiveBasedOnConfig() {

    var result = await(componentClient.forKeyValueEntity("test")
      .method(TestCounterEntity::get)
      .invokeAsync());

    assertThat(result).isEqualTo(100);
  }

  @Test
  public void verifyIfComponentIsDisabledBasedOnConfig() {

    var exc1 = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      await(componentClient.forKeyValueEntity("test")
        .method(ProdCounterEntity::get)
        .invokeAsync());
    });

    assertThat(exc1.getMessage()).contains("Unknown entity type [prod-counter]");

    var exc2 = Assertions.assertThrows(IllegalArgumentException.class, () -> {
      await(componentClient.forKeyValueEntity("test")
        .method(StageCounterEntity::get)
        .invokeAsync());
    });

    assertThat(exc2.getMessage()).contains("Unknown entity type [stage-counter]");
  }

  @Test
  public void verifyUserConfig() {
    var result = await(componentClient.forKeyValueEntity("test")
        .method(TestCounterEntity::getUserConfigKeys)
        .invokeAsync(Set.of(
            // user defined config
            "user-app.config-value",
            // should also be able to read sdk config
            "akka.javasdk.dev-mode.service-name",
            // but not other akka settings
            "akka.actor.provider"
            )));

    // from src/test/resources/application.conf
    assertThat(result).containsEntry("user-app.config-value", "some value");
    assertThat(result).containsEntry("akka.javasdk.dev-mode.service-name", "sdk-tests");
    assertThat(result).doesNotContainKey("akka.actor.provider");
  }

  @Test
  public void verifyEchoActionWiring() {

    timerScheduler.startSingleTimer("echo-action", ofMillis(0), componentClient.forTimedAction()
      .method(EchoAction::stringMessage)
      .deferred("hello"));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = StaticTestBuffer.getValue("echo-action");
        assertThat(value).isEqualTo("hello");
      });
  }

  @Test
  public void verifyHierarchyTimedActionWiring() {

    timerScheduler.startSingleTimer("wired", ofMillis(0), componentClient.forTimedAction()
      .method(HierarchyTimed::stringMessage)
      .deferred("hello"));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = StaticTestBuffer.getValue("hierarchy-action");
        assertThat(value).isEqualTo("hello");
      });
  }

  @Test
  public void verifyTimedActionListCommand() {

    timerScheduler.startSingleTimer("echo-action", ofMillis(0), componentClient.forTimedAction()
      .method(EchoAction::stringMessages)
      .deferred(List.of("hello", "mr")));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = StaticTestBuffer.getValue("echo-action");
        assertThat(value).isEqualTo("hello mr");
      });

    timerScheduler.startSingleTimer("echo-action", ofMillis(0), componentClient.forTimedAction()
      .method(EchoAction::commandMessages)
      .deferred(List.of(new EchoAction.SomeCommand("tambourine"), new EchoAction.SomeCommand("man"))));

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = StaticTestBuffer.getValue("echo-action");
        assertThat(value).isEqualTo("tambourine man");
      });
  }

  @Test
  public void verifyTimedActionEmpty() {
    timerScheduler.startSingleTimer("echo-action", ofMillis(0), componentClient.forTimedAction()
      .method(EchoAction::emptyMessage)
      .deferred());

    Awaitility.await()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var value = StaticTestBuffer.getValue("echo-action");
        assertThat(value).isEqualTo("empty");
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
    counterClient.method(CounterEntity::increase).invoke(1);
    counterClient.method(CounterEntity::times).invoke(2);
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
  public void verifyFindCounterByValue() {

    var emptyCounter = await(
      componentClient.forView()
        .method(CountersByValue::getCounterByValue)
        .invokeAsync(CountersByValue.queryParam(101)));

    assertThat(emptyCounter).isEmpty();

    await(
      componentClient.forEventSourcedEntity("abc")
        .method(CounterEntity::increase)
        .invokeAsync(101));


    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .untilAsserted(
        () -> {
          var byValue = await(
            componentClient.forView()
              .method(CountersByValue::getCounterByValue)
              .invokeAsync(CountersByValue.queryParam(101)));

          assertThat(byValue).hasValue(new Counter(101));
        });
  }


  @Test
  public void verifyUserSubscriptionAction() {

    TestUser user = new TestUser("123", "john345@doe.com", "JohnDoe");

    createUser(user);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> UserSideEffect.getUsers().get(user.id()),
        new IsEqual(new User(user.name(), user.email())));

    deleteUser(user);

    var isDeleted = await(componentClient
      .forKeyValueEntity(user.id())
      .method(UserEntity::getDelete)
      .invokeAsync());

    assertThat(isDeleted).isEqualTo(true);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(() -> UserSideEffect.getUsers().get(user.id()),
        new IsNull<>());
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
        var header = StaticTestBuffer.getValue(ActionWithMetadata.SOME_HEADER);
        assertThat(header).isEqualTo(metadataValue);
      });
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

  private void createUser(TestUser user) {
    Ok userCreation =
      await(
        componentClient.forKeyValueEntity(user.id())
          .method(UserEntity::createOrUpdateUser)
          .invokeAsync(new UserEntity.CreatedUser(user.name(), user.email())));
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
          .forKeyValueEntity(user.id())
          .method(UserEntity::deleteUser)
          .invokeAsync(new UserEntity.Delete()));
    assertThat(userDeleted).isEqualTo(Ok.instance);
  }


}

