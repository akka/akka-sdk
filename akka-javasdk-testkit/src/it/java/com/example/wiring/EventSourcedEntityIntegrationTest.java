/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import akka.javasdk.http.StrictResponse;
import akka.javasdk.testkit.TestKitSupport;
import com.example.wiring.eventsourcedentities.counter.Counter;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import akka.javasdk.client.EventSourcedEntityClient;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

public class EventSourcedEntityIntegrationTest extends TestKitSupport {

  @Test
  public void verifyCounterEventSourcedWiring() {

    var counterId = "hello";
    var client = componentClient.forEventSourcedEntity(counterId);

    Integer counterIncrease = increaseCounter(client, 10);
    Assertions.assertEquals(10, counterIncrease);

    Integer counterMultiply = multiplyCounter(client, 20);
    Assertions.assertEquals(200, counterMultiply);

    int counterGet = getCounter(client);
    Assertions.assertEquals(200, counterGet);
  }

  @Test
  public void verifyCounterErrorEffect() {

    CompletableFuture<StrictResponse<String>> call = httpClient.POST("/akka/v1.0/entity/counter-entity/c001/increaseWithError")
      .withRequestBody(-10)
      .responseBodyAs(String.class)
      .invokeAsync()
      .toCompletableFuture();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(5, TimeUnit.SECONDS)
      .untilAsserted(() -> {

        assertThat(call).isCompletedExceptionally();
        assertThat(call.exceptionNow()).isInstanceOf(IllegalArgumentException.class);
        assertThat(call.exceptionNow().getMessage()).contains("Value must be greater than 0");
      });
  }

  @Test
  public void verifyCounterResultResponse() {

    var client = componentClient.forEventSourcedEntity("testing");

    Result<CounterEntity.Error, Counter> result = await(client
      .method(CounterEntity::increaseWithResult)
      .invokeAsync(-10));

    assertThat(result.error()).isEqualTo(CounterEntity.Error.TOO_LOW);

    Result<CounterEntity.Error, Counter> result2 = await(client
      .method(CounterEntity::increaseWithResult)
      .invokeAsync(1000001));

    assertThat(result2.error()).isEqualTo(CounterEntity.Error.TOO_HIGH);

    Result<CounterEntity.Error, Counter> result3 = await(client
      .method(CounterEntity::increaseWithResult)
      .invokeAsync(123));

    assertThat(result3.success()).isEqualTo(new Counter(123));
  }

  @Test
  public void verifyCounterEventSourcedAfterRestart() {

    var counterId = "helloRestart";
    var client = componentClient.forEventSourcedEntity(counterId);

    increaseCounter(client, 15);
    multiplyCounter(client, 2);
    int counterGet = getCounter(client);
    Assertions.assertEquals(30, counterGet);

    // force restart of counter entity
    restartCounterEntity(client);

    // events should be replayed successfully and
    // counter value should be the same as previously
    Awaitility.await()
            .ignoreExceptions()
            .atMost(20, TimeUnit.SECONDS)
            .until(() ->
              getCounter(client), new IsEqual(30));
  }

  @Test
  public void verifyCounterEventSourcedAfterRestartFromSnapshot() {

    // snapshotting with kalix.event-sourced-entity.snapshot-every = 10
    var counterId = "restartFromSnapshot";
    var client = componentClient.forEventSourcedEntity(counterId);

    // force the entity to snapshot
    for (int i = 0; i < 10; i++) {
      increaseCounter(client, 1);
    }
    Assertions.assertEquals(10, getCounter(client));

    // force restart of counter entity
    restartCounterEntity(client);

    // current state is based on snapshot and should be the same as previously
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> getCounter(client),
        new IsEqual(10));
  }

  @Test
  public void verifyRequestWithDefaultProtoValuesWithEntity() {
    var client = componentClient.forEventSourcedEntity("some-counter");
    increaseCounter(client, 2);
    Integer result = await(client.method(CounterEntity::set).invokeAsync(0));
    assertThat(result).isEqualTo(0);
  }


  private Integer increaseCounter(EventSourcedEntityClient client, int value) {
    return await(client
      .method(CounterEntity::increase)
      .invokeAsync(value));
  }


  private Integer multiplyCounter(EventSourcedEntityClient client, int value) {
    return await(client
      .method(CounterEntity::times)
      .invokeAsync(value));
  }

  private void restartCounterEntity(EventSourcedEntityClient client) {
    try {
      await(client
        .method(CounterEntity::restart).invokeAsync());
      fail("This should not be reached");
    } catch (Exception ignored) {
    }
  }

  private Integer getCounter(EventSourcedEntityClient client) {
    return await(client.method(CounterEntity::get).invokeAsync());
  }

}