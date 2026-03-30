/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static akka.javasdk.impl.MetadataImpl.CeSubject;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.CloudEvent;
import akka.javasdk.testkit.ConsumerResult;
import akka.javasdk.testkit.ConsumerTestKit;
import akka.javasdk.testkit.MockComponentClient;
import akkajavasdk.components.eventsourcedentities.counter.CounterEntity;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import akkajavasdk.components.eventsourcedentities.counter.IncreaseConsumer;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity;
import akkajavasdk.components.pubsub.PublishVEToTopic;
import java.net.URI;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public class ConsumerTestkitTest {

  private final CloudEvent cloudEvent = CloudEvent.of("test-id", URI.create("test"), "test-type");

  @Test
  public void shouldHandleEventWithMockedComponentClient() {
    // given
    MockComponentClient mockClient = MockComponentClient.create();

    // stub: when CounterEntity::increase is called for entity "entity-1", return 43
    mockClient.forEventSourcedEntity("entity-1").method(CounterEntity::increase).thenReturn(43);

    var testKit = ConsumerTestKit.of(() -> new IncreaseConsumer(mockClient));

    // when - ValueIncreased with value 42 triggers a call to CounterEntity::increase
    ConsumerResult result =
        testKit
            .<CounterEvent.ValueIncreased>method(IncreaseConsumer::printIncrease)
            .withMetadata(cloudEvent.withSubject("entity-1").asMetadata())
            .invoke(new CounterEvent.ValueIncreased(42));

    // then - the consumer should complete successfully (asyncDone)
    assertThat(result.isConsumed()).isTrue();
  }

  @Test
  public void shouldHandleEventWithoutComponentClientCall() {
    // given
    MockComponentClient mockClient = MockComponentClient.create();
    var testKit = ConsumerTestKit.of(() -> new IncreaseConsumer(mockClient));

    // when - ValueIncreased with value != 42 doesn't trigger a component call
    ConsumerResult result =
        testKit
            .<CounterEvent.ValueIncreased>method(IncreaseConsumer::printIncrease)
            .withMetadata(cloudEvent.withSubject("entity-1").asMetadata())
            .invoke(new CounterEvent.ValueIncreased(10));

    // then
    assertThat(result.isConsumed()).isTrue();
  }

  @Test
  public void shouldProduceMsg() {
    // given
    var testKit = ConsumerTestKit.of(PublishVEToTopic::new);
    CustomerEntity.Customer consumer = new CustomerEntity.Customer("andre", Instant.now());
    String subject = "entity-2";

    // when
    ConsumerResult result =
        testKit
            .method(PublishVEToTopic::handleChange)
            .withMetadata(cloudEvent.withSubject(subject).asMetadata())
            .invoke(consumer);

    // then
    assertThat(result.isProduced()).isTrue();
    assertThat(result.getProducedMessage(CustomerEntity.Customer.class)).isEqualTo(consumer);
    assertThat(result.getMetadata().get(CeSubject()).get()).isEqualTo(subject);
  }
}
