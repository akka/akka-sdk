package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpClient;
import akka.javasdk.timedaction.TimedAction;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// tag::expire-order[]
@ComponentId("order-timed-action") // <1>
public class OrderTimedAction extends TimedAction { // <2>

  private final ComponentClient componentClient;

  public OrderTimedAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect expireOrder(String orderId) {
    return effects().asyncDone(
        componentClient.forKeyValueEntity(orderId)
            .method(OrderEntity::cancel) // <3>
            .invokeAsync()
            .thenApply(__ -> Done.done())); // <4>
  }

}
// end::expire-order[]
