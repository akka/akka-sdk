package shoppingcart.polymorphism.domain;

import akka.javasdk.annotations.TypeName;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import shoppingcart.domain.ShoppingCart;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ShoppingCartEvent.OpenShoppingCartEvent.class, name = "open"),
  @JsonSubTypes.Type(value = ShoppingCartEvent.CheckedOutShoppingCartEvent.class, name = "checked-out")})
public sealed interface ShoppingCartEvent {

  sealed interface OpenShoppingCartEvent extends ShoppingCartEvent {
    @TypeName("item-added") // <2>
    record ItemAdded(ShoppingCart.LineItem item) implements OpenShoppingCartEvent {
    }

    @TypeName("item-removed")
    record ItemRemoved(String productId) implements OpenShoppingCartEvent {
    }

    @TypeName("checked-out")
    record CheckedOut() implements OpenShoppingCartEvent {
    }
  }

  sealed interface CheckedOutShoppingCartEvent extends ShoppingCartEvent {
    @TypeName("cancelled")
    record Cancelled() implements CheckedOutShoppingCartEvent {
    }
  }
}
