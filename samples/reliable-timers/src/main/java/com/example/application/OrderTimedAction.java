package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;

// tag::expire-order[]
@ComponentId("order-timed-action") // <1>
public class OrderTimedAction extends TimedAction { // <2>

  private final ComponentClient componentClient;

  public OrderTimedAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect expireOrder(String orderId) {
    var result = componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel) // <3>
        .invoke();
    return switch (result) { // <4>
      case OrderEntity.Result.Invalid ignored -> effects().done();
      case OrderEntity.Result.NotFound ignored -> effects().done();
      case OrderEntity.Result.Ok ignored -> effects().done();
    };
  }
  // end::expire-order[]

  // the code bellow is added as support for the documentation
  // tag::expire-order-legacy[]
  public record ExpireOrder(String orderId) {
  }
  // tag::expire-order-legacy-noops[]
  public Effect expire(ExpireOrder orderId) {
  // end::expire-order-legacy-noops[]
    return expireOrder(orderId.orderId());
  }
  // end::expire-order-legacy[]

  public Effect expireNoops(ExpireOrder orderId) {
    // tag::expire-order-legacy-noops[]
    return effects().done();
  }
  // end::expire-order-legacy-noops[]

// tag::expire-order[]
}
// end::expire-order[]
