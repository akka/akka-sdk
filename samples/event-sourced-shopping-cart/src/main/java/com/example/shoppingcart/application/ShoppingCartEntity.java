package com.example.shoppingcart.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import com.example.shoppingcart.domain.ShoppingCart;
import com.example.shoppingcart.domain.ShoppingCart.LineItem;
import com.example.shoppingcart.domain.ShoppingCartEvent;

import java.util.Collections;

// tag::class[]
@ComponentId("shopping-cart") // <2>
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart, ShoppingCartEvent> { // <1>
  // end::class[]

  // tag::getCart[]
  private final String entityId;

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId(); // <1>
  }

  @Override
  public ShoppingCart emptyState() { // <2>
    return new ShoppingCart(entityId, Collections.emptyList(), false);
  }

  // end::getCart[]

  public Effect<Done> create() {
    return effects().reply(Done.getInstance());
  }

  // tag::addItem[]
  public Effect<Done> addItem(LineItem item) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");
    if (item.quantity() <= 0) { // <1>
      return effects().error("Quantity for item " + item.productId() + " must be greater than zero.");
    }

    var event = new ShoppingCartEvent.ItemAdded(item); // <2>

    return effects()
      .persist(event) // <3>
      .thenReply(newState -> Done.getInstance()); // <4>
  }

  // end::addItem[]

  public Effect<Done> removeItem(String productId) {
    if (currentState().checkedOut())
      return effects().error("Cart is already checked out.");
    if (currentState().findItemByProductId(productId).isEmpty()) {
      return effects().error("Cannot remove item " + productId + " because it is not in the cart.");
    }

    var event = new ShoppingCartEvent.ItemRemoved(productId);

    return effects()
      .persist(event)
      .thenReply(newState -> Done.getInstance());
  }

  // tag::getCart[]
  // tag::read-only[]
  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState()); // <3>
  }
  // end::read-only[]
  // end::getCart[]

  // tag::checkout[]
  public Effect<Done> checkout() {
    if (currentState().checkedOut())
      return effects().reply(Done.getInstance());

    return effects()
      .persist(new ShoppingCartEvent.CheckedOut()) // <1>
      .deleteEntity() // <2>
      .thenReply(newState -> Done.getInstance());
  }
  // end::checkout[]

  // tag::addItem[]
  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case ShoppingCartEvent.ItemAdded evt -> currentState().onItemAdded(evt); // <5>
      // end::addItem[]
      case ShoppingCartEvent.ItemRemoved evt -> currentState().onItemRemoved(evt);
      case ShoppingCartEvent.CheckedOut evt -> currentState().onCheckedOut();
      // tag::addItem[]
    };
  }
  // end::addItem[]
// tag::class[]
}
// end::class[]