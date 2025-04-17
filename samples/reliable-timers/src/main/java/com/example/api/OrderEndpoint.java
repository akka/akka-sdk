package com.example.api;

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

// Opened up for access from the public internet to make the sample service easy to try out.
// For actual services meant for production this must be carefully considered, and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
// tag::timers[]
@HttpEndpoint("/orders")
public class OrderEndpoint {
// end::timers[]

  private final Logger logger = LoggerFactory.getLogger(getClass());

  // tag::place-order[]
  private final TimerScheduler timerScheduler;
  private final ComponentClient componentClient;

  public OrderEndpoint(TimerScheduler timerScheduler, ComponentClient componentClient) { // <1>
    this.timerScheduler = timerScheduler;
    this.componentClient = componentClient;
  }

  private String timerName(String orderId) {
    return "order-expiration-timer-" + orderId;
  }

  @Post
  public Order placeOrder(OrderRequest orderRequest) {

    var orderId = UUID.randomUUID().toString(); // <2>

    timerScheduler.createSingleTimer( // <3>
      timerName(orderId), // <4>
      Duration.ofSeconds(10), // <5>
      componentClient.forTimedAction()
        .method(OrderTimedAction::expireOrder)
        .deferred(orderId) // <6>
    );

    // end::place-order[]
    logger.info(
      "Placing order for item {} (quantity {}). Order number '{}'",
      orderRequest.item(),
      orderRequest.quantity(),
      orderId);
    // tag::place-order[]

    var order = componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::placeOrder)
        .invoke(orderRequest); // <7>

    return order;
  }
  // end::place-order[]

  // tag::confirm-order[]
  // ...

  @Post("/{orderId}/confirm")
  public HttpResponse confirm(String orderId) {
    // end::confirm-order[]
    logger.info("Confirming order '{}'", orderId);
    // tag::confirm-order[]
    var confirmResult = componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::confirm).invoke(); // <1>

    return switch (confirmResult) {
      case OrderEntity.Result.Ok ignored -> {
        timerScheduler.delete(timerName(orderId)); // <2>
        yield HttpResponses.ok();
      }
      case OrderEntity.Result.NotFound notFound ->
          HttpResponses.notFound(notFound.message());
      case OrderEntity.Result.Invalid invalid ->
          HttpResponses.badRequest(invalid.message());
    };
  }
  // end::confirm-order[]

  @Post("/{orderId}/cancel")
  public HttpResponse cancel(String orderId) {
    logger.info("Cancelling order '{}'", orderId);

    componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel)
        .invoke();
    timerScheduler.delete(timerName(orderId));

    return HttpResponses.ok();
  }

// tag::timers[]
}
// end::timers[]
