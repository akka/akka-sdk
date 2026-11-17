package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;

// tag::start-from-latest[]
@Component(id = "cart-notifier")
@Consume.FromEventSourcedEntity(ShoppingCartEntityWithNotifications.class)
@Consume.StartFrom(latest = true)
public class CartNotifier extends Consumer {

  public Effect onEvent(ShoppingCartEntityWithNotifications.CartEvent event) {
    // send a notification
    return effects().done();
  }
}
// end::start-from-latest[]
