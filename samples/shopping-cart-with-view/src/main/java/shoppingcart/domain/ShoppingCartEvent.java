package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ShoppingCartEvent {

  @TypeName("item-added")
  record ItemAdded(String cartId, String userId, String productId, String name, int quantity, String description)
      implements ShoppingCartEvent {
  }

  @TypeName("item-removed")
  record ItemRemoved(String cartId, String productId) implements ShoppingCartEvent {
  }

  @TypeName("checked-out")
  record CheckedOut(String cartId, String userId) implements ShoppingCartEvent {
  }
}
