// tag::top[]
package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;
// end::top[]
// tag::events[]
// tag::domain[]

public sealed interface ShoppingCartEvent { // <1>

  @TypeName("item-added-old") // <2>
  record ItemAdded(ShoppingCart.LineItem item) implements ShoppingCartEvent {
  }

// end::domain[]

  @TypeName("item-removed-old")
  record ItemRemoved(String productId) implements ShoppingCartEvent {
  }

  @TypeName("checked-out-old")
  record CheckedOut() implements ShoppingCartEvent {
  }
// tag::domain[]
}
// end::domain[]
// end::events[]
