package com.example.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.application.ShoppingCartDTO.LineItemDTO;
import com.example.domain.ShoppingCart;

import java.time.Instant;

/**
 * A key value entity.
 */
// tag::summary[]
@ComponentId("shopping-cart")
public class ShoppingCartEntity extends KeyValueEntity<ShoppingCart> {
  // end::summary[]
  @SuppressWarnings("unused")
  private final String entityId;

  public ShoppingCartEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public ShoppingCart emptyState() {
    return ShoppingCart.of(entityId);
  }

  // tag::create[]
  // tag::summary[]

  public Effect<ShoppingCartDTO> create() {
    //...
    // end::summary[]
    if (currentState().creationTimestamp() > 0L) {
      return effects().error("Cart was already created");
    } else {
      var newState = currentState().withCreationTimestamp(Instant.now().toEpochMilli());
      return effects()
        .updateState(newState)
        .thenReply(ShoppingCartDTO.of(newState));
    }
  }
  // end::create[]

  // tag::add-item[]
  // tag::summary[]

  public Effect<ShoppingCartDTO> addItem(LineItemDTO addLineItem) {
    //...
    // end::summary[]
    if (addLineItem.quantity() <= 0) {
      return effects()
        .error("Quantity for item " + addLineItem.productId() + " must be greater than zero.");
    }

    var newState = currentState().withItem(addLineItem.toDomain());
    return effects()
      .updateState(newState)
      .thenReply(ShoppingCartDTO.of(newState));
  }

  // end::add-item[]
  public Effect<ShoppingCartDTO> removeItem(String productId) {
    var lineItemOpt = currentState().findItemByProductId(productId);

    if (lineItemOpt.isEmpty()) {
      return effects()
        .error("Cannot remove item " + productId + " because it is not in the cart.");
    }

    var newState = currentState().withoutItem(lineItemOpt.get());
    return effects()
      .updateState(newState)
      .thenReply(ShoppingCartDTO.of(newState));
  }

  // tag::get-cart[]
  // tag::summary[]

  public Effect<ShoppingCartDTO> getCart() {
    //...
    // end::summary[]
    return effects().reply(ShoppingCartDTO.of(currentState()));
  }
  // end::get-cart[]

  public Effect<String> removeCart() {
    var userRole = commandContext().metadata().get("Role").get();
    if (userRole.equals("Admin")) {
      return effects().deleteEntity().thenReply("OK");
    } else {
      return effects().error("Only admin can remove the cart");
    }
  }
// tag::summary[]
}
// end::summary[]

