/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity;
import akkajavasdk.components.eventsourcedentities.protobuf.ProtobufCustomerEntity.CreateCustomerCommand;
import akkajavasdk.components.keyvalueentities.protobuf.ProtobufCustomerKvEntity;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration test for a consumer that subscribes to protobuf events from an event sourced entity
 * using a single GeneratedMessageV3 handler.
 */
@ExtendWith(Junit5LogCapturing.class)
public class ProtobufConsumerIntegrationTest extends TestKitSupport {

  @Test
  public void shouldConsumeProtobufEventsFromEntity() {
    var customerId = "consumer-proto-test-1";
    var esClient = componentClient.forEventSourcedEntity(customerId);

    // Create customer - this emits a CustomerCreated protobuf event
    esClient
        .method(ProtobufCustomerEntity::create)
        .invoke(new CreateCustomerCommand("Alice Proto", "alice@proto.com"));

    // The consumer should receive the event and store info in a KV entity
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              var kvState =
                  componentClient
                      .forKeyValueEntity(customerId)
                      .method(ProtobufCustomerKvEntity::get)
                      .invoke();
              // Consumer stores "created:<name>:<email>" as the name field
              assertThat(kvState.getName()).isEqualTo("created:Alice Proto:alice@proto.com");
            });
  }
}
