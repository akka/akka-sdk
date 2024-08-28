package com.example.actions;

import com.example.domain.Order;
import com.example.domain.OrderEntity;
import com.example.domain.OrderRequest;
import akka.javasdk.annotations.http.Endpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

// tag::timers[]
@Endpoint("/orders")
public class OrderEndpoint {
// end::timers[]

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient componentClient;

  public OrderEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  // tag::place-order[]
  private String timerName(String orderId) {
    return "order-expiration-timer-" + orderId;
  }

  @Post("/place")
  public CompletionStage<Order> placeOrder(OrderRequest orderRequest) {

    var orderId = UUID.randomUUID().toString(); // <1>

    // FIXME no timer access in endpoint
    /*
    CompletionStage<Done> timerRegistration = // <2>
      timers().startSingleTimer(
        timerName(orderId), // <3>
        Duration.ofSeconds(10), // <4>
        componentClient.forAction().method(OrderAction::expire).deferred(orderId) // <5>
      );
    */

    // end::place-order[]
    logger.info(
      "Placing order for item {} (quantity {}). Order number '{}'",
      orderRequest.item(),
      orderRequest.quantity(),
      orderId);
    // tag::place-order[]

    var request =
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::placeOrder).deferred(orderRequest); // <6>

    throw new UnsupportedOperationException("No timers in endpoints yet");
    /* return  // <7>
      timerRegistration
        .thenCompose(done -> request.invokeAsync())
        .thenApply(order -> order)
    ); */
  }
  // end::place-order[]

  // tag::expire-order[]
  // ...

  @Post("/expire/{orderId}")
  public CompletionStage<String> expire(String orderId) {
    logger.info("Expiring order '{}'", orderId);
    CompletionStage<String> reply =
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel).invokeAsync() // <1>
        .thenApply(result -> {
          // Entity can return Ok, NotFound or Invalid.
          // Those are valid response and should not trigger a re-try.
          // In case of exceptions, this method call will fail.
          return "Ok";
        });
    return reply;
  }
  // end::expire-order[]

  // tag::confirm-cancel-order[]
  // ...

  @Post("/confirm/{orderId}")
  public CompletionStage<String> confirm(String orderId) {
    logger.info("Confirming order '{}'", orderId);

    CompletionStage<String> reply =
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::confirm).invokeAsync() // <1>
        // FIXME no timer support in endpoints yet .thenCompose(result -> timers().cancel(timerName(orderId))) // <2>
        .thenApply(done -> "Ok");

    return reply;
  }

  @Post("/cancel/{orderId}")
  public CompletionStage<String> cancel(String orderId) {
    logger.info("Cancelling order '{}'", orderId);

    CompletionStage<String> reply =
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel).invokeAsync()
          // FIXME no timer support in endpoints yet.thenCompose(req -> timers().cancel(timerName(orderId)))
        .thenApply(done -> "Ok");

    return reply;
  }
  // end::confirm-cancel-order[]

// tag::timers[]
}
// end::timers[]
