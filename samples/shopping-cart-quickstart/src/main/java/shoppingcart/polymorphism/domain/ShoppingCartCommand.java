package shoppingcart.polymorphism.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import shoppingcart.domain.ShoppingCart;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ShoppingCartCommand.OpenShoppingCartCommand.class, name = "open"),
  @JsonSubTypes.Type(value = ShoppingCartCommand.CheckedOutShoppingCartCommand.class, name = "checked-out")})
public sealed interface ShoppingCartCommand {

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = OpenShoppingCartCommand.AddItem.class, name = "a"),
    @JsonSubTypes.Type(value = OpenShoppingCartCommand.RemoveItem.class, name = "r"),
    @JsonSubTypes.Type(value = OpenShoppingCartCommand.Checkout.class, name = "c")})
  sealed interface OpenShoppingCartCommand extends ShoppingCartCommand {
    record AddItem(ShoppingCart.LineItem item) implements OpenShoppingCartCommand {
    }

    record RemoveItem(String productId) implements OpenShoppingCartCommand {
    }

    record Checkout() implements OpenShoppingCartCommand {
    }
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = CheckedOutShoppingCartCommand.Cancel.class, name = "cc")})
  sealed interface CheckedOutShoppingCartCommand extends ShoppingCartCommand {

    record Cancel() implements CheckedOutShoppingCartCommand {
    }
  }
}
