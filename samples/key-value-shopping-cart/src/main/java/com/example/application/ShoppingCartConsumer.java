package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.consumer.Consumer;
import com.example.domain.ShoppingCart;

// tag::kve-consumer[]
@ComponentId("shopping-cart-consumer")
@Consume.FromKeyValueEntity(ShoppingCartEntity.class) // <1>
public class ShoppingCartConsumer extends Consumer {

  public Effect onChange(ShoppingCart shoppingCart) { // <2>
    //processing shopping cart change
    return effects().done();
  }

  @DeleteHandler
  public Effect onDelete() { // <3>
    //processing shopping cart delete
    return effects().done();
  }
}
// end::kve-consumer[]
