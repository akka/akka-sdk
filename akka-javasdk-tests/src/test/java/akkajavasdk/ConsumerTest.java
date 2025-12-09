/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import akkajavasdk.components.eventsourcedentities.counter.RawPayloadCounterConsumer;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ConsumerTest extends TestKitSupport {

  @Test
  public void allowsPassthroughOfRawEventBytes() {
    RawPayloadCounterConsumer.seenEvents.clear();
    assertThat(RawPayloadCounterConsumer.seenEvents).isEmpty();
    try {
      var entityId = UUID.randomUUID().toString();
      componentClient.forEventSourcedEntity(entityId).method(CounterEntity::increase).invoke(5);

      Awaitility.await()
          .untilAsserted(
              () -> {
                assertThat(RawPayloadCounterConsumer.seenEvents)
                    .contains((new CounterEvent.ValueIncreased(5)).toString());
              });

      componentClient.forEventSourcedEntity(entityId).method(CounterEntity::times).invoke(5);

      Awaitility.await()
          .untilAsserted(
              () -> {
                assertThat(RawPayloadCounterConsumer.seenEvents).hasSize(2);
                assertThat(RawPayloadCounterConsumer.seenEvents)
                    .contains("raw-bytes: application/json; type=multiplied");
              });

    } finally {
      RawPayloadCounterConsumer.seenEvents.clear();
    }
  }
}
