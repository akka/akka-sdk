/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static akkajavasdk.components.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity;
import akkajavasdk.components.keyvalueentities.customer.CustomerEntity.Customer;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventingTestkitDestinationTest extends TestKitSupport {

  private EventingTestKit.OutgoingMessages destination;

  public TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    destination = testKit.getTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  @Test
  public void shouldPublishEventWithTypeNameViaEventingTestkit()
      throws ExecutionException, InterruptedException, TimeoutException {
    // given
    String subject = "test";
    Customer customer = new Customer("andre", Instant.now());

    // when
    componentClient
        .forKeyValueEntity(subject)
        .method(CustomerEntity::create)
        .invokeAsync(customer)
        .toCompletableFuture()
        .get(5, TimeUnit.SECONDS);

    // then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.of(SECONDS))
        .untilAsserted(
            () -> {
              Customer publishedCustomer = destination.expectOneTyped(Customer.class).getPayload();
              assertThat(publishedCustomer).isEqualTo(customer);
            });
  }
}
