/*
 * Copyright (C) 2024 Lightbend Inc. <https://www.lightbend.com>
 */
package com.example.ttl;

// tag::expire[]
import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.TypeName;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import java.time.Duration;

@Component(id = "shopping-cart")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> {

  public Effect<Done> addItem(String productId) {
    return effects()
      .persist(new ShoppingCartEvent.ItemAdded(productId))
      .expireAfter(Duration.ofDays(30)) // <1>
      .thenReply(__ -> Done.getInstance());
  }

  // end::expire[]

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return currentState();
  }
  // tag::expire[]
}

// end::expire[]

record ShoppingCart(String id) {}

sealed interface ShoppingCartEvent {
  @TypeName("item-added")
  record ItemAdded(String productId) implements ShoppingCartEvent {}
}
