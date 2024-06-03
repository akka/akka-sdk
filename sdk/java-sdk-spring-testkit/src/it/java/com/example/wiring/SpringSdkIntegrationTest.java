/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import com.example.Main;
import com.example.wiring.actions.echo.ActionWithHttpResponse;
import com.example.wiring.actions.echo.ActionWithMetadata;
import com.example.wiring.actions.echo.EchoAction;
import com.example.wiring.actions.echo.Message;
import com.example.wiring.actions.headers.ForwardHeadersAction;
import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import com.example.wiring.eventsourcedentities.headers.ForwardHeadersESEntity;
import com.example.wiring.valueentities.customer.CustomerEntity;
import com.example.wiring.valueentities.headers.ForwardHeadersValueEntity;
import com.example.wiring.valueentities.user.AssignedCounterEntity;
import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserEntity;
import com.example.wiring.valueentities.user.UserSideEffect;
import com.example.wiring.views.CountersByValue;
import com.example.wiring.views.CountersByValueWithIgnore;
import com.example.wiring.views.CustomerByCreationTime;
import com.example.wiring.views.UserCounter;
import com.example.wiring.views.UserCounters;
import com.example.wiring.views.UserCountersView;
import com.example.wiring.views.UserWithVersion;
import com.example.wiring.views.UserWithVersionView;
import com.example.wiring.views.UsersByEmailAndName;
import com.example.wiring.views.UsersView;
import kalix.javasdk.HttpResponse;
import kalix.javasdk.Metadata;
import kalix.javasdk.StatusCode;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.client.EventSourcedEntityClient;
import kalix.spring.KalixConfigurationTest;
import kalix.spring.testkit.AsyncCallsSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.SECONDS;
import static kalix.javasdk.StatusCode.Success.CREATED;
import static kalix.javasdk.StatusCode.Success.OK;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Main.class)
@Import({KalixConfigurationTest.class, TestkitConfig.class})
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public class SpringSdkIntegrationTest extends AsyncCallsSupport {

  @Autowired
  private WebClient webClient;

  @Autowired
  private ComponentClient componentClient;

  private Duration timeout = Duration.of(10, SECONDS);

  @Test
  public void failRequestWithRequiredQueryParam() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/optional-params-action")
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("Required request parameter is missing: longValue");
  }

  @Test
  public void notAcceptRequestWithMissingPathParamIfNotEntityId() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/echo/message/") // missing param
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  public void failRequestWithMissingRequiredIntPathParam() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/echo/int/") // missing param
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString())))
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("Path contains value of wrong type! Expected field of type INT32.");
  }

  @Test
  public void shouldReturnTextBody() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Type")).contains("text/plain");
    assertThat(response.getBody()).isEqualTo("test");
  }

  @Test
  public void shouldReturnTextBodyWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().methodRef(ActionWithHttpResponse::textBody).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(OK);
    assertThat(response.getContentType()).contains("text/plain");
    assertThat(response.getBody()).contains("test".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void shouldReturnEmptyCreatedMethod() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/empty-text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/octet-stream");
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void shouldReturnEmptyCreatedWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().methodRef(ActionWithHttpResponse::emptyCreated).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(CREATED);
    assertThat(response.getContentType()).isEqualTo("application/octet-stream");
    assertThat(response.getBody()).isEmpty();
  }

  @Test
  public void shouldReturnJsonString() {

    ResponseEntity<Message> response =
      webClient
        .get()
        .uri("/json-string-body")
        .retrieve()
        .toEntity(Message.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/json");
    assertThat(response.getBody()).isEqualTo(new Message("123"));
  }

  @Test
  public void shouldReturnJsonStringWithComponentClient() {

    HttpResponse response = await(componentClient.forAction().methodRef(ActionWithHttpResponse::jsonStringBody).invokeAsync());

    assertThat(response.getStatusCode()).isEqualTo(OK);
    assertThat(response.getContentType()).contains("application/json");
    assertThat(response.getBody()).contains("{\"text\": \"123\"}".getBytes());
    assertThat(response.bodyAsJson(Message.class)).isEqualTo(new Message("123"));
  }

  @Test
  public void shouldReturnEmptyBody() {

    ResponseEntity<String> response =
      webClient
        .get()
        .uri("/empty-text-body")
        .retrieve()
        .toEntity(String.class)
        .block(timeout);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().get("Content-Type")).contains("application/octet-stream");
    assertThat(response.getBody()).isNull();
  }

  @Test
  public void verifyRequestWithOptionalQueryParams() {

    Message response =
      webClient
        .get()
        .uri("/optional-params-action?longValue=1")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("1nullnull");
  }

  @Test
  public void verifyRequestWithProtoDefaultValues() {

    Message response =
      webClient
        .get()
        .uri("/action/0/0/0/0?shortValue=0&byteValue=0&charValue=97&booleanValue=false")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("0.00.00000afalse");
  }

  @Test
  public void verifyJavaPrimitivesAsParams() {

    Message response =
      webClient
        .get()
        .uri("/action/1.0/2.0/3/4?shortValue=5&byteValue=6&charValue=97&booleanValue=true")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(response.text()).isEqualTo("1.02.03456atrue");

    Message responseCollections =
      webClient
        .get()
        .uri("/action_collections?ints=1&ints=0&ints=2")
        .retrieve()
        .bodyToMono(Message.class)
        .block(timeout);

    assertThat(responseCollections.text()).isEqualTo("1,0,2");
  }

  @Test
  public void verifyEchoActionWiring() {

    Message response = await(
      componentClient.forAction()
        .methodRef(EchoAction::stringMessage)
        .invokeAsync("abc"));

    assertThat(response.text()).isEqualTo("Parrot says: 'abc'");
  }


  @Test
  public void verifyEchoActionRequestParam() {

    Message response = await(
      componentClient.forAction()
        .methodRef(EchoAction::stringMessageFromParam)
        .invokeAsync("queryParam"));

    assertThat(response.text()).isEqualTo("Parrot says: 'queryParam'");

    var failedReq =
      webClient
        .get()
        .uri("/echo/message")
        .retrieve()
        .toEntity(String.class)
        .onErrorResume(WebClientResponseException.class, error -> {
          if (error.getStatusCode().is4xxClientError()) {
            return Mono.just(ResponseEntity.status(error.getStatusCode()).body(error.getResponseBodyAsString()));
          } else {
            return Mono.error(error);
          }
        })
        .block(timeout);
    assertThat(failedReq.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(failedReq.getBody()).contains("Required request parameter is missing: msg");
  }

  @Test
  public void verifyEchoActionConcatBody() {

    var message = List.of(new Message("foo"), new Message("bar"));
    Message response = await(
      componentClient.forAction()
        .methodRef(EchoAction::stringMessageConcatRequestBody)
        .invokeAsync(message)
    );

    assertThat(response.text()).isEqualTo("foo|bar");
  }

  @Test
  public void verifyEchoActionConcatBodyWithSeparator() {

    var message = List.of(new Message("foo"), new Message("bar"));
    Message response = await(
      componentClient.forAction()
        .methodRef(EchoAction::stringMessageConcatRequestBodyWithSeparator)
        .deferred("/", message).invokeAsync()
    );

    assertThat(response.text()).isEqualTo("foo/bar");
  }

  @Test
  public void verifyEchoActionWithCustomCode() {
    ClientResponse response =
      webClient
        .post()
        .uri("/echo/message/customCode/hello")
        .exchangeToMono(Mono::just)
        .block(timeout);
    Assertions.assertEquals(StatusCode.Success.ACCEPTED.value(), response.statusCode().value());
  }


  @Test
  public void verifyEchoActionRequestParamWithTypedForward() {

    String reqParam = "a b&c@d";
    Message response = await(
      componentClient.forAction()
        .methodRef(EchoAction::stringMessageFromParamFwTyped)
        .invokeAsync(reqParam));

    assertThat(response.text()).isEqualTo("Parrot says: '" + reqParam + "'");
  }

  @Test
  public void verifyStreamActions() {

    List<Message> messageList =
      webClient
        .get()
        .uri("/echo/repeat/abc/times/3")
        .retrieve()
        .bodyToFlux(Message.class)
        .toStream()
        .collect(Collectors.toList());

    assertThat(messageList).hasSize(3);
  }

  @Test
  public void verifyCounterEventSourceSubscription() {
    // GIVEN IncreaseAction is subscribed to CounterEntity events
    // WHEN the CounterEntity is requested to increase 42\
    String entityId = "hello1";
    await(
      componentClient.forEventSourcedEntity(entityId)
        .methodRef(CounterEntity::increase)
        .invokeAsync(42));

    // THEN IncreaseAction receives the event 42 and increases the counter 1 more
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer result = await(
          componentClient.forEventSourcedEntity(entityId)
            .methodRef(CounterEntity::get).invokeAsync());

        assertThat(result).isEqualTo(43); //42 +1
      });
  }

  @Test
  public void verifySideEffects() {
    // GIVEN IncreaseAction is subscribed to CounterEntity events
    // WHEN the CounterEntity is requested to increase 4422
    String entityId = "hello4422";
    await(
      componentClient.forEventSourcedEntity(entityId)
        .methodRef(CounterEntity::increase)
        .invokeAsync(4422));

    // THEN IncreaseAction receives the event 4422 and increases the counter 1 more
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Integer result = await(
          componentClient.forEventSourcedEntity(entityId)
            .methodRef(CounterEntity::get).invokeAsync());

        assertThat(result).isEqualTo(4423);
      });
  }

  @Test
  public void verifyActionIsNotSubscribedToMultiplyAndRouterIgnores() {
    var entityId = "counterId2";
    EventSourcedEntityClient counterClient = componentClient.forEventSourcedEntity(entityId);
    await(counterClient.methodRef(CounterEntity::increase).invokeAsync(1));
    await(counterClient.methodRef(CounterEntity::times).invokeAsync(2));
    Integer lastKnownValue = await(counterClient.methodRef(CounterEntity::increase).invokeAsync(1234));

    assertThat(lastKnownValue).isEqualTo(1 * 2 + 1234);

    //Once the action IncreaseActionWithIgnore processes event 1234 it adds 1 more to the counter
    Awaitility.await()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        Integer result = await(
          componentClient.forEventSourcedEntity(entityId)
            .methodRef(CounterEntity::get).invokeAsync());

        assertThat(result).isEqualTo(1 * 2 + 1234 + 1);
      });
  }

  @Test
  public void verifyViewIsNotSubscribedToMultiplyAndRouterIgnores() {

    var entityId = "counterId4";
    EventSourcedEntityClient counterClient = componentClient.forEventSourcedEntity(entityId);
    await(counterClient.methodRef(CounterEntity::increase).invokeAsync(1));
    await(counterClient.methodRef(CounterEntity::times).invokeAsync(2));
    Integer counterGet = await(counterClient.methodRef(CounterEntity::increase).invokeAsync(1));

    assertThat(counterGet).isEqualTo(1 * 2 + 1);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(
        () -> {
          var byValue = await(
            componentClient.forView()
              .methodRef(CountersByValueWithIgnore::getCounterByValue)
              .invokeAsync(2));

          assertThat(byValue.value()).isEqualTo(1 + 1);
        });
  }

  @Test
  public void verifyFindCounterByValue() {

    await(
      componentClient.forEventSourcedEntity("abc")
        .methodRef(CounterEntity::increase)
        .invokeAsync(10));


    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .untilAsserted(
        () -> {
          var byValue = await(
            componentClient.forView()
              .methodRef(CountersByValue::getCounterByValue)
              .invokeAsync(10));

          assertThat(byValue.value()).isEqualTo(10);
        });
  }

  @Test
  public void verifyCounterViewMultipleSubscriptions() {

    await(
      componentClient.forEventSourcedEntity("hello2")
        .methodRef(CounterEntity::increase)
        .invokeAsync(1));

    await(
      componentClient.forEventSourcedEntity("hello3")
        .methodRef(CounterEntity::increase)
        .invokeAsync(1));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(
        () ->
          webClient
            .get()
            .uri("/counters-ms/by-value/1")
            .retrieve()
            .bodyToFlux(Counter.class)
            .toStream()
            .collect(Collectors.toList())
            .size(),
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
      .ignoreExceptions()
      .atMost(15, TimeUnit.of(SECONDS))
      .until(
        () ->
          webClient
            .get()
            .uri("/users/by-email/" + user.email)
            .exchangeToMono(clientResponse -> Mono.just(clientResponse.statusCode()))
            .block(timeout)
            .value(),
        new IsEqual(404));

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
              .methodRef(UsersView::getUsersEmail)
              .invokeAsync(user.email));

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
          var byName = await(
            componentClient.forView()
              .methodRef(UsersView::getUsersByName)
              .invokeAsync(user.name));
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
          var byEmail = await(
            componentClient.forView()
              .methodRef(UsersByEmailAndName::getUsers).deferred(user.email, user.name).invokeAsync());
          assertThat(byEmail.email).isEqualTo(user.email);
          assertThat(byEmail.name).isEqualTo(user.name);
        });
  }

  @Test
  public void verifyFindUsersByNameStreaming() {

    TestUser joe1 = new TestUser("user1", "john@doe.com", "joe");
    TestUser joe2 = new TestUser("user2", "john@doe.com", "joe");
    createUser(joe1);
    createUser(joe2);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() -> getUsersByName("joe").size(),
        new IsEqual(2));
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
  public void verifyForwardHeaders() {

    String actionHeaderValue = "action-value";
    String veHeaderValue = "ve-value";
    String esHeaderValue = "es-value";

    Message actionResponse =
      await(
        componentClient.forAction()
          .methodRef(ForwardHeadersAction::stringMessage)
          .withMetadata(Metadata.EMPTY.add(ForwardHeadersAction.SOME_HEADER, actionHeaderValue))
          .invokeAsync()
      );

    assertThat(actionResponse.text()).isEqualTo(actionHeaderValue);

    Message veResponse =
      await(
        componentClient.forValueEntity("1")
          .methodRef(ForwardHeadersValueEntity::createUser).deferred()
          .withMetadata(Metadata.EMPTY.add(ForwardHeadersAction.SOME_HEADER, veHeaderValue))
          .invokeAsync()
      );

    assertThat(veResponse.text()).isEqualTo(veHeaderValue);

    Message esResponse =
      await(
        componentClient.forEventSourcedEntity("1")
          .methodRef(ForwardHeadersESEntity::createUser).deferred()
          .withMetadata(Metadata.EMPTY.add(ForwardHeadersAction.SOME_HEADER, esHeaderValue))
          .invokeAsync()
      );

    assertThat(esResponse.text()).isEqualTo(esHeaderValue);
  }

  @Test
  public void shouldPropagateMetadataWithHttpDeferredCall() {
    String value = "someValue";

    Message actionResponse =
      await(
        componentClient
          .forAction()
          .methodRef(ActionWithMetadata::actionWithMeta)
          .deferred("myKey", value).invokeAsync()
      );

    assertThat(actionResponse.text()).isEqualTo(value);
  }

  @Test
  public void shouldSupportMetadataInReplies() {
    String value = "someValue";

    String headerInResponse =
      webClient
        .get()
        .uri("/reply-meta/myKey/" + value)
        .exchangeToMono(response -> Mono.just(Objects.requireNonNull(
          response.headers().asHttpHeaders().getFirst("myKey"))))
        .block();

    assertThat(value).isEqualTo(headerInResponse);

    String headerInAyncResponse =
      webClient
        .get()
        .uri("/reply-async-meta/myKey/" + value)
        .exchangeToMono(response -> Mono.just(Objects.requireNonNull(
          response.headers().asHttpHeaders().getFirst("myKey"))))
        .block();

    assertThat(value).isEqualTo(headerInAyncResponse);
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
    return webClient
      .get()
      .uri("/users/by-name/" + name)
      .retrieve()
      .bodyToFlux(User.class)
      .toStream()
      .collect(Collectors.toList());
  }

  @Nullable
  private UserWithVersion getUserByEmail(String email) {
    return await(componentClient.forView().methodRef(UserWithVersionView::getUser).invokeAsync(email));
  }

  private void updateUser(TestUser user) {
    Ok userUpdate =
      await(
        componentClient.forValueEntity(user.id)
          .methodRef(UserEntity::createOrUpdateUser)
          .invokeAsync(new UserEntity.CreatedUser(user.name, user.email)));

    assertThat(userUpdate).isEqualTo(Ok.instance);
  }

  private void createUser(TestUser user) {
    Ok userCreation =
      await(
        componentClient.forValueEntity(user.id)
          .methodRef(UserEntity::createOrUpdateUser)
          .invokeAsync(new UserEntity.CreatedUser(user.name, user.email)));
    assertThat(userCreation).isEqualTo(Ok.instance);
  }


  private void createCustomer(CustomerEntity.Customer customer) {

    Ok created =
      await(
        componentClient
          .forValueEntity(customer.name())
          .methodRef(CustomerEntity::create)
          .invokeAsync(customer));

    assertThat(created).isEqualTo(Ok.instance);
  }


  @NotNull
  private List<CustomerEntity.Customer> getCustomersByCreationDate(Instant createdOn) {
    return await(
      componentClient.forView()
        .methodRef(CustomerByCreationTime::getCustomerByTime)
        .invokeAsync(new CustomerByCreationTime.ByTimeRequest(createdOn)))
      .customers();
  }


  private void deleteUser(TestUser user) {
    Ok userDeleted =
      await(
        componentClient
          .forValueEntity(user.id)
          .methodRef(UserEntity::deleteUser)
          .invokeAsync(new UserEntity.Delete()));
    assertThat(userDeleted).isEqualTo(Ok.instance);
  }

  private void increaseCounter(String id, int value) {
    await(
      componentClient.forEventSourcedEntity(id)
        .methodRef(CounterEntity::increase)
        .invokeAsync(value));
  }

  private void multiplyCounter(String id, int value) {
    await(
      componentClient.forEventSourcedEntity(id)
        .methodRef(CounterEntity::times)
        .invokeAsync(value));
  }

  private void assignCounter(String id, String assignee) {
    await(
      componentClient.forValueEntity(id)
        .methodRef(AssignedCounterEntity::assign)
        .invokeAsync(assignee));
  }

  private UserCounters getUserCounters(String userId) {
    return await(
      componentClient.forView().methodRef(UserCountersView::get)
        .invokeAsync(userId));
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
