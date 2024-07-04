package com.example;

import com.example.domain.Order;
import com.example.domain.OrderEntity;
import com.example.domain.OrderRequest;
import com.example.domain.OrderStatus;

import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

public class OrderEndpointIntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(20, SECONDS);

  /* FIXME two problems here:
    1. we need to allow timer access in endpoints
    2. this specific sample endpoint wants to call itself with a timer (so needs to have an action as well or something?)

  @Test
  public void placeOrder() {

    var orderReq = new OrderRequest("nice swag tshirt", 10);
    String orderId = placeOrder(orderReq);
    Assertions.assertNotNull(orderId);
    Assertions.assertFalse(orderId.isEmpty());

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> getOrderStatus(orderId),
        s -> s.quantity() == 10 && s.item().equals("nice swag tshirt"));

    var confirmResp = confirmOrder(orderId);
    Assertions.assertEquals("\"Ok\"", confirmResp);

    Awaitility.await()
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

    var methodRef =
        componentClient
            .forValueEntity(orderId)
            .method(OrderEntity::status);

    // After the default timeout, status changed to not placed as order is reverted
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .until(
        () -> await(methodRef.invokeAsync()),
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
    return await(httpClient.POST("/orders/confirm/" + orderId)
            .responseBodyAs(String.class).invokeAsync(), timeout)
            .body();
  }

  private String expireOrder(String orderId) {
    return await(httpClient.POST("/orders/expire/" + orderId)
            .responseBodyAs(String.class)
            .invokeAsync(), timeout).body();
  }

  private String placeOrder(OrderRequest orderReq) {
    return await(httpClient.POST("/orders/place")
                    .withRequestBody(orderReq)
                    .responseBodyAs(Order.class)
                    .invokeAsync(),
            timeout).body().id();
  }

  private OrderStatus getOrderStatus(String orderId) {
    return await(
      componentClient
        .forValueEntity(orderId)
        .method(OrderEntity::status).invokeAsync()
    );

  }

   */


}
