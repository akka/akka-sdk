package com.example.api;

import akka.Done;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.timer.TimerScheduler;
import com.example.application.OrderEntity;
import com.example.application.OrderTimedAction;
import com.example.domain.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::timers[]
@HttpEndpoint("/orders")
public class OrderEndpoint {
// end::timers[]

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final ComponentClient componentClient;
  private final TimerScheduler timerScheduler;

  public OrderEndpoint(ComponentClient componentClient, TimerScheduler timerScheduler) {
    this.componentClient = componentClient;
    this.timerScheduler = timerScheduler;
  }

  // tag::place-order[]
  private String timerName(String orderId) {
    return "order-expiration-timer-" + orderId;
  }

  @Post
  public CompletionStage<Order> placeOrder(OrderRequest orderRequest) {

    var orderId = UUID.randomUUID().toString(); // <1>

    CompletionStage<Done> timerRegistration = // <2>
      timerScheduler.startSingleTimer(
        timerName(orderId), // <3>
        Duration.ofSeconds(10), // <4>
        componentClient.forTimedAction()
          .method(OrderTimedAction::expireOrder)
          .deferred(orderId) // <5>
      );

    // end::place-order[]
    logger.info(
      "Placing order for item {} (quantity {}). Order number '{}'",
      orderRequest.item(),
      orderRequest.quantity(),
      orderId);
    // tag::place-order[]

    return
      timerRegistration.thenCompose(done ->
          componentClient.forKeyValueEntity(orderId)
            .method(OrderEntity::placeOrder)
            .invokeAsync(orderRequest)) // <6>
        .thenApply(order -> order);

  }
  // end::place-order[]

  // tag::confirm-cancel-order[]
  // ...

  @Post("/{orderId}/confirm")
  public CompletionStage<HttpResponse> confirm(String orderId) {
    logger.info("Confirming order '{}'", orderId);

    return
        componentClient.forKeyValueEntity(orderId)
            .method(OrderEntity::confirm).invokeAsync() // <1>
            .thenCompose(result ->
                switch (result) {
                  case OrderEntity.Result.Ok __ -> timerScheduler.cancel(timerName(orderId)) // <2>
                      .thenApply(___ -> HttpResponses.ok());
                  case OrderEntity.Result.NotFound notFound ->
                      CompletableFuture.completedFuture(HttpResponses.notFound(notFound.message()));
                  case OrderEntity.Result.Invalid invalid ->
                      CompletableFuture.completedFuture(HttpResponses.badRequest(invalid.message()));
                });
  }

  @Post("/{orderId}/cancel")
  public CompletionStage<HttpResponse> cancel(String orderId) {
    logger.info("Cancelling order '{}'", orderId);

    return
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel).invokeAsync()
        .thenCompose(req ->
          timerScheduler.cancel(timerName(orderId)))
        .thenApply(done -> HttpResponses.ok());
  }
  // end::confirm-cancel-order[]

// tag::timers[]
}
// end::timers[]
