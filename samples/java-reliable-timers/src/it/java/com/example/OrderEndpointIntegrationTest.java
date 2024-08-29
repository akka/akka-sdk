package com.example;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.AkkaSdkTestKitSupport;
import com.example.api.OrderRequest;
import com.example.application.OrderEntity;
import com.example.domain.Order;
import com.example.domain.OrderStatus;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class OrderEndpointIntegrationTest extends AkkaSdkTestKitSupport {

  private Duration timeout = Duration.of(20, SECONDS);

  @Test
  public void placeOrder() {

    var orderReq = new OrderRequest("nice swag tshirt", 10);
    String orderId = placeOrder(orderReq);
    assertThat(orderId).isNotEmpty();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.of(SECONDS))
      .untilAsserted(
        () -> {
          OrderStatus orderStatus = getOrderStatus(orderId);
          assertThat(orderStatus.quantity()).isEqualTo(10);
          assertThat(orderStatus.item()).isEqualTo("nice swag tshirt");
        });

    var confirmResp = confirmOrder(orderId);
    assertThat(confirmResp.status()).isEqualTo(StatusCodes.OK);

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

    assertThat(orderId).isNotEmpty();

    var methodRef =
        componentClient
            .forKeyValueEntity(orderId)
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
    HttpResponse resp = expireOrder("made-up-id");
    assertThat(resp.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void expireConfirmedOrder() {
    // the 'expire' endpoint is made to be used internally by timers
    // thus, in case the order is already confirmed, it should return successfully so the timer is not rescheduled

    var orderReq = new OrderRequest("nice swag tshirt", 20);
    String orderId = placeOrder(orderReq);

    var confirmResp = confirmOrder(orderId);
    assertThat(confirmResp.status()).isEqualTo(StatusCodes.OK);

    var expireResp = expireOrder("made-up-id");
    assertThat(expireResp.status()).isEqualTo(StatusCodes.OK);
  }


  private HttpResponse confirmOrder(String orderId) {
    return await(httpClient.POST("/orders/" + orderId + "/confirm")
            .invokeAsync()).httpResponse();
  }

  private HttpResponse expireOrder(String orderId) {
    return await(httpClient.POST("/orders/" + orderId + "/expire")
            .invokeAsync()).httpResponse();
  }

  private String placeOrder(OrderRequest orderReq) {
    return await(httpClient.POST("/orders")
                    .withRequestBody(orderReq)
                    .responseBodyAs(Order.class)
                    .invokeAsync(),
            timeout).body().id();
  }

  private OrderStatus getOrderStatus(String orderId) {
    return await(
      componentClient
        .forKeyValueEntity(orderId)
        .method(OrderEntity::status).invokeAsync()
    );
  }


}
