package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

// tag::events[]

public sealed interface ShoppingCartEvent { // <1>

  @TypeName("item-added") // <2>
  record ItemAdded(ShoppingCart.LineItem item) implements ShoppingCartEvent {
  }

  @TypeName("item-removed")
  record ItemRemoved(String productId) implements ShoppingCartEvent {
  }

  @TypeName("checked-out")
  record CheckedOut() implements ShoppingCartEvent {
  }
}
// end::events[]
