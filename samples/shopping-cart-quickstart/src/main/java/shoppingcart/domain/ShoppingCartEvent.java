package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface ShoppingCartEvent {
  @TypeName("item-added")
  record ItemAdded(ShoppingCart.LineItem item) implements ShoppingCartEvent {
  }

  @TypeName("item-removed")
  record ItemRemoved(String productId) implements ShoppingCartEvent {
  }

  @TypeName("checked-out")
  record CheckedOut() implements ShoppingCartEvent {
  }
}
