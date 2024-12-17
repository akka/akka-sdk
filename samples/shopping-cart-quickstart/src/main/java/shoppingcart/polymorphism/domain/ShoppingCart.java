package shoppingcart.polymorphism.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = OpenShoppingCart.class, name = "open"),
  @JsonSubTypes.Type(value = CheckedOutShoppingCart.class, name = "checked-out")})
public sealed interface ShoppingCart permits OpenShoppingCart, CheckedOutShoppingCart {

  ShoppingCartEvent handleCommand(ShoppingCartCommand command);

  ShoppingCart applyEvent(ShoppingCartEvent event);
}
