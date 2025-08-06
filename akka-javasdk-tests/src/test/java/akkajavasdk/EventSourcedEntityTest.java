/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import akka.javasdk.CommandException;
import akka.javasdk.client.EventSourcedEntityClient;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.MyException;
import akkajavasdk.components.eventsourcedentities.counter.Counter;
import akkajavasdk.components.eventsourcedentities.counter.CounterCommand;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.hierarchy.AbstractTextConsumer;
import akkajavasdk.components.eventsourcedentities.hierarchy.TextEsEntity;
import com.typesafe.config.ConfigFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class EventSourcedEntityTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
        ConfigFactory.parseString(
            """
            akka.javasdk.event-sourced-entity.snapshot-every = 10
            """));
  }

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
  public void verifyEventSourcedEntityRunsOnVirtualThread() {
    var result =
        componentClient
            .forEventSourcedEntity("hello")
            .method(CounterEntity::commandHandlerIsOnVirtualThread)
            .invoke();
    assertThat(result).isTrue();
  }

  @Test
  public void verifyCounterEventSourcedDeletion() {
    var counterId = "deleted-hello";
    var client = componentClient.forEventSourcedEntity(counterId);

    var isDeleted = client.method(CounterEntity::getDeleted).invoke();
    assertThat(isDeleted).isFalse();

    client.method(CounterEntity::delete).invoke();

    var isDeleted2 = client.method(CounterEntity::getDeleted).invoke();
    assertThat(isDeleted2).isTrue();
  }

  @Test
  public void verifyCounterErrorEffect() {
    var counterId = "hello-error";
    var client = componentClient.forEventSourcedEntity(counterId);
    assertThrows(IllegalArgumentException.class, () -> increaseCounterWithError(client, -1));
  }

  @Test
  public void verifyCounterResultResponse() {

    var client = componentClient.forEventSourcedEntity("testing");

    Result<CounterEntity.Error, Counter> result =
        await(client.method(CounterEntity::increaseWithResult).invokeAsync(-10));

    assertThat(result.error()).isEqualTo(CounterEntity.Error.TOO_LOW);

    Result<CounterEntity.Error, Counter> result2 =
        await(client.method(CounterEntity::increaseWithResult).invokeAsync(1000001));

    assertThat(result2.error()).isEqualTo(CounterEntity.Error.TOO_HIGH);

    Result<CounterEntity.Error, Counter> result3 =
        await(client.method(CounterEntity::increaseWithResult).invokeAsync(123));

    assertThat(result3.success()).isEqualTo(new Counter(123));
  }

  @Test
  public void verifyCounterGenericCommand() {

    var client = componentClient.forEventSourcedEntity("testing-generics");

    Integer result1 =
        await(client.method(CounterEntity::multiIncrease).invokeAsync(List.of(1, 5, 7)));

    assertThat(result1).isEqualTo(13);

    Integer result2 =
        await(
            client
                .method(CounterEntity::multiIncreaseCommands)
                .invokeAsync(
                    List.of(new CounterEntity.DoIncrease(1), new CounterEntity.DoIncrease(5))));

    assertThat(result2).isEqualTo(19);
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
        .until(() -> getCounter(client), new IsEqual(30));
  }

  @Test
  public void verifyCounterEventSourcedAfterRestartFromSnapshot() {

    // snapshotting with akka.javasdk.event-sourced-entity.snapshot-every = 10
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
        .until(() -> getCounter(client), new IsEqual(10));
  }

  @Test
  public void verifyRequestWithSealedCommand() {
    var client = componentClient.forEventSourcedEntity("counter-with-sealed-command-handler");
    await(client.method(CounterEntity::handle).invokeAsync(new CounterCommand.Set(123)));

    Integer result1 = client.method(CounterEntity::get).invoke();
    assertThat(result1).isEqualTo(123);

    await(client.method(CounterEntity::handle).invokeAsync(new CounterCommand.Increase(123)));

    Integer result2 = client.method(CounterEntity::get).invoke();
    assertThat(result2).isEqualTo(246);
  }

  @Test
  public void verifyRequestWithDefaultProtoValuesWithEntity() {
    var client = componentClient.forEventSourcedEntity("some-counter");
    increaseCounter(client, 2);
    Integer result = client.method(CounterEntity::set).invoke(0);
    assertThat(result).isEqualTo(0);
  }

  @Test
  public void testHierarchyEntity() {
    var client = componentClient.forEventSourcedEntity("some-id");

    client.method(TextEsEntity::setText).invoke("my text");

    var result = client.method(TextEsEntity::getText).invoke();
    assertThat(result).isEqualTo(Optional.of("my text"));

    // also verify that hierarchy consumer works
    Awaitility.await()
        .untilAsserted(
            () ->
                assertThat(StaticTestBuffer.getValue(AbstractTextConsumer.BUFFER_KEY))
                    .isEqualTo("my text"));
  }

  @Test
  public void shouldTestExceptions() {
    var exc1 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient
                  .forEventSourcedEntity("1")
                  .method(CounterEntity::run)
                  .invoke("errorMessage");
            });
    assertThat(exc1.getMessage()).isEqualTo("errorMessage");

    var exc2 =
        Assertions.assertThrows(
            CommandException.class,
            () -> {
              componentClient
                  .forEventSourcedEntity("1")
                  .method(CounterEntity::run)
                  .invoke("errorCommandException");
            });
    assertThat(exc2.getMessage()).isEqualTo("errorCommandException");

    var exc3 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forEventSourcedEntity("1")
                  .method(CounterEntity::run)
                  .invoke("errorMyException");
            });
    assertThat(exc3.getMessage()).isEqualTo("errorMyException");
    assertThat(exc3.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc4 =
        Assertions.assertThrows(
            MyException.class,
            () -> {
              componentClient
                  .forEventSourcedEntity("1")
                  .method(CounterEntity::run)
                  .invoke("throwMyException");
            });
    assertThat(exc4.getMessage()).isEqualTo("throwMyException");
    assertThat(exc4.getData()).isEqualTo(new MyException.SomeData("some data"));

    var exc5 =
        Assertions.assertThrows(
            RuntimeException.class,
            () -> {
              componentClient
                  .forEventSourcedEntity("1")
                  .method(CounterEntity::run)
                  .invoke("throwRuntimeException");
            });
    assertThat(exc5.getMessage())
        .contains("Unexpected failure: java.lang.RuntimeException: throwRuntimeException");
  }

  private Integer increaseCounter(EventSourcedEntityClient client, int value) {
    return client.method(CounterEntity::increase).invoke(value);
  }

  private Counter increaseCounterWithError(EventSourcedEntityClient client, int value) {
    return client.method(CounterEntity::increaseWithError).invoke(value);
  }

  private Integer multiplyCounter(EventSourcedEntityClient client, int value) {
    return client.method(CounterEntity::times).invoke(value);
  }

  private void restartCounterEntity(EventSourcedEntityClient client) {
    try {
      client.method(CounterEntity::restart).invoke();
      fail("This should not be reached");
    } catch (Exception ignored) {
    }
  }

  private Integer getCounter(EventSourcedEntityClient client) {
    return client.method(CounterEntity::get).invoke();
  }
}
