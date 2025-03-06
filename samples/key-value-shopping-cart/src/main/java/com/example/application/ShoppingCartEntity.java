package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.domain.ShoppingCart;

import java.time.Instant;


@ComponentId("shopping-cart")
public class ShoppingCartEntity extends KeyValueEntity<ShoppingCart> {
  @SuppressWarnings("unused")
  private final String entityId;

  public ShoppingCartEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public ShoppingCart emptyState() {
    return ShoppingCart.of(entityId);
  }

  public Effect<ShoppingCart> create() {
    if (currentState().creationTimestamp().isAfter(Instant.EPOCH)) {
      return effects().error("Cart was already created");
    } else {
      var newState = currentState().withCreationTimestamp(Instant.now());
      return effects()
        .updateState(newState)
        .thenReply(newState);
    }
  }

  public Effect<ShoppingCart> addItem(ShoppingCart.LineItem addLineItem) {
    if (addLineItem.quantity() <= 0) {
      return effects()
        .error("Quantity for item " + addLineItem.productId() + " must be greater than zero.");
    }

    var newState = currentState().withItem(addLineItem);
    return effects()
      .updateState(newState)
      .thenReply(newState);
  }

  public Effect<ShoppingCart> removeItem(String productId) {
    var lineItemOpt = currentState().findItemByProductId(productId);

    if (lineItemOpt.isEmpty()) {
      return effects()
        .error("Cannot remove item " + productId + " because it is not in the cart.");
    }

    var newState = currentState().withoutItem(lineItemOpt.get());
    return effects()
      .updateState(newState)
      .thenReply(newState);
  }

  public ReadOnlyEffect<ShoppingCart> getCart() {
    return effects().reply(currentState());
  }

  public Effect<String> removeCart() {
    var userRole = commandContext().metadata().get("Role").get();
    if (userRole.equals("Admin")) {
      return effects().deleteEntity().thenReply("OK");
    } else {
      return effects().error("Only admin can remove the cart");
    }
  }
}

