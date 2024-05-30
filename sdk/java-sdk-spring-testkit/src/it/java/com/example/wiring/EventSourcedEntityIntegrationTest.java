/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring;

import com.example.Main;
import com.example.wiring.eventsourcedentities.counter.CounterEntity;
import kalix.javasdk.client.ComponentClient;
import kalix.javasdk.client.EventSourcedEntityClient;
import kalix.spring.KalixConfigurationTest;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.concurrent.TimeUnit;

import static kalix.javasdk.testkit.DeferredCallSupport.execute;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(classes = Main.class)
@Import(KalixConfigurationTest.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
public class EventSourcedEntityIntegrationTest {

    @Autowired
    private ComponentClient componentClient;

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
        int counterGet2 = getCounter(client);
        Assertions.assertEquals(30, counterGet2);
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
        await()
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
        Integer result = execute(client.call(CounterEntity::set).params(0));
        assertThat(result).isEqualTo(0);
    }


    private Integer increaseCounter(EventSourcedEntityClient client, int value)  {
        return execute(client
            .call(CounterEntity::increase)
            .params(value));
    }


    private Integer multiplyCounter(EventSourcedEntityClient client, int value) {
        return execute(client
            .call(CounterEntity::times)
            .params(value));
    }

    private void restartCounterEntity(EventSourcedEntityClient client) {
        try {
            execute(client
                .call(CounterEntity::restart));
            fail("This should not be reached");
        } catch (Exception ignored) {
        }
    }

    private Integer getCounter(EventSourcedEntityClient client) {
        return execute(client
            .call(CounterEntity::get));
    }

}