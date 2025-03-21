package shoppingcart.domain;

import akka.javasdk.annotations.TypeName;

public sealed interface UserEvent {

  @TypeName("cart-closed")
  record UserCartClosed(String userId, String newCartId) implements UserEvent {
  }
}
