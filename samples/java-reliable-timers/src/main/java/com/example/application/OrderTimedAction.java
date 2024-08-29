package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;

@ComponentId("order-timed-action")
public class OrderTimedAction extends TimedAction {

  private final ComponentClient componentClient;

  public OrderTimedAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect expireOrder(String orderId) {
    return effects().asyncDone(
      componentClient.forKeyValueEntity(orderId)
        .method(OrderEntity::cancel)
        .invokeAsync()
        .thenApply(__ -> Done.done()));
  }
}
