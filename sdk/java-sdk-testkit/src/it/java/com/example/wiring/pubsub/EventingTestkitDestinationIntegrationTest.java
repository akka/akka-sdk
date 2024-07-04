/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.valueentities.customer.CustomerEntity;
import com.example.wiring.valueentities.customer.CustomerEntity.Customer;
import akka.platform.javasdk.testkit.EventingTestKit;
import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.example.wiring.pubsub.PublishVEToTopic.CUSTOMERS_TOPIC;
import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventingTestkitDestinationIntegrationTest extends KalixIntegrationTestKitSupport {

  private EventingTestKit.OutgoingMessages destination;

  public KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    destination = kalixTestKit.getTopicOutgoingMessages(CUSTOMERS_TOPIC);
  }

  @Test
  public void shouldPublishEventWithTypeNameViaEventingTestkit() throws ExecutionException, InterruptedException, TimeoutException {
    //given
    String subject = "test";
    Customer customer = new Customer("andre", Instant.now());

    //when
    componentClient
      .forValueEntity(subject)
      .method(CustomerEntity::create)
      .invokeAsync(customer)
      .toCompletableFuture().get(5, TimeUnit.SECONDS);

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        Customer publishedCustomer = destination.expectOneTyped(Customer.class).getPayload();
        assertThat(publishedCustomer).isEqualTo(customer);
      });
  }
}
