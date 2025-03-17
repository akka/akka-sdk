package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ShoppingCartEvent {

  @TypeName("item-added")
  record ItemAdded(String userId, String productId, String name, int quantity, String description)
      implements ShoppingCartEvent {
  }

  @TypeName("item-removed")
  record ItemRemoved(String userId, String productId) implements ShoppingCartEvent {
  }

  @TypeName("checked-out")
  record CheckedOut(String userId) implements ShoppingCartEvent {
  }
}
