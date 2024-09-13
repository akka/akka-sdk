package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import com.example.domain.ShoppingCart;

@ComponentId("shopping-cart-consumer")
@Consume.FromKeyValueEntity(ShoppingCartEntity.class)
public class ShoppingCartConsumer extends Consumer {

  public Effect onChange(ShoppingCart shoppingCart) {
    //processing shopping cart change
    return effects().done();
  }
}
