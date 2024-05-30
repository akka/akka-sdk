package com.example;

import com.example.domain.Order;
import com.example.domain.OrderEntity;
import com.example.domain.OrderRequest;
import com.example.domain.OrderStatus;
import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.client.ComponentClient;
import static kalix.javasdk.testkit.DeferredCallSupport.execute;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
public class OrderActionIntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(20, SECONDS);



  @Autowired
  private WebClient webClient;

  @Autowired
  private ComponentClient componentClient;


  @Test
  public void placeOrder() {

    var orderReq = new OrderRequest("nice swag tshirt", 10);
    String orderId = placeOrder(orderReq);
    Assertions.assertNotNull(orderId);
    Assertions.assertFalse(orderId.isEmpty());

    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> getOrderStatus(orderId),
        s -> s.quantity() == 10 && s.item().equals("nice swag tshirt"));

    var confirmResp = confirmOrder(orderId);
    Assertions.assertEquals("\"Ok\"", confirmResp);

    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> getOrderStatus(orderId),
        OrderStatus::confirmed);

  }

  @Test
  public void expiredOrder() {

    var orderReq = new OrderRequest("nice swag tshirt", 20);
    String orderId = placeOrder(orderReq);

    Assertions.assertNotNull(orderId);
    Assertions.assertFalse(orderId.isEmpty());

    var deferredCall =
        componentClient
            .forValueEntity(orderId)
            .call(OrderEntity::status);

    // After the default timeout, status changed to not placed as order is reverted
    await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> execute(deferredCall),
        status -> !status.confirmed());
  }

  @Test
  public void expireNonexistentOrder() {
    // the 'expire' endpoint is made to be used internally by timers
    // thus, in case the order does not exist, it should return successfully so the timer is not rescheduled
    String resp = expireOrder("made-up-id");
    Assertions.assertNotNull(resp);
    Assertions.assertEquals("\"Ok\"", resp);
  }

  @Test
  public void expireConfirmedOrder() {
    // the 'expire' endpoint is made to be used internally by timers
    // thus, in case the order is already confirmed, it should return successfully so the timer is not rescheduled

    var orderReq = new OrderRequest("nice swag tshirt", 20);
    String orderId = placeOrder(orderReq);

    var confirmResp = confirmOrder(orderId);
    Assertions.assertEquals("\"Ok\"", confirmResp);

    String resp = expireOrder("made-up-id");
    Assertions.assertNotNull(resp);
    Assertions.assertEquals("\"Ok\"", resp);
  }

  private String confirmOrder(String orderId) {
    return webClient.post()
        .uri("/orders/confirm/" + orderId)
        .retrieve()
        .bodyToMono(String.class)
        .block(timeout);
  }

  private String expireOrder(String orderId) {
    return webClient.post()
        .uri("/orders/expire/" + orderId)
        .retrieve()
        .bodyToMono(String.class)
        .block(timeout);
  }

  private String placeOrder(OrderRequest orderReq) {
    return webClient.post()
        .uri("/orders/place")
        .bodyValue(orderReq)
        .retrieve()
        .bodyToMono(Order.class)
        .block(timeout)
        .id();
  }

  private OrderStatus getOrderStatus(String orderId) {
    return execute(
      componentClient
        .forValueEntity(orderId)
        .call(OrderEntity::status)
    );

  }


}
