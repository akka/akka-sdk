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

  // end::expire-order[]
  private final ComponentClient componentClient;
  private final HttpClient httpClient;

  public OrderTimedAction(ComponentClient componentClient, HttpClient httpClient) {
    this.componentClient = componentClient;
    this.httpClient = httpClient;
  }

  // tag::expire-order[]
  public Effect expireOrder(String orderId) {
    return effects().asyncDone(
      canExpireOrder(orderId) // <3>
        .thenCompose(canExpire -> {
          if (canExpire) {
            return componentClient.forKeyValueEntity(orderId)
              .method(OrderEntity::cancel) // <4>
              .invokeAsync()
              .thenApply(__ -> Done.done());
          } else { // <5>
            //handle the case where the order cannot be expired
            return CompletableFuture.completedFuture(Done.done());
          }
        }));
  }

  public CompletionStage<Boolean> canExpireOrder(String orderId) {
    //use the httpClient to check if the order can be expired
    return CompletableFuture.completedFuture(true);
  }
}
// end::expire-order[]
