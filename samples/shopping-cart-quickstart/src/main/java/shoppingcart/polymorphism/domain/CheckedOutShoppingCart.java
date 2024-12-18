package shoppingcart.polymorphism.domain;

import shoppingcart.domain.ShoppingCart.LineItem;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.CheckedOutShoppingCartCommand;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.CheckedOutShoppingCartCommand.Cancel;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.CheckedOutShoppingCartEvent;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.CheckedOutShoppingCartEvent.Cancelled;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.OpenShoppingCartEvent;

import java.util.List;

public record CheckedOutShoppingCart(String cartId, List<LineItem> items, Boolean cancelled) implements ShoppingCart {

  @Override
  public ShoppingCartEvent handleCommand(ShoppingCartCommand command) {
    return switch (command) {
      case OpenShoppingCartCommand __ -> throw new IllegalStateException("Cannot handle command on a checked out cart");
      case CheckedOutShoppingCartCommand checkedOutCommand -> switch (checkedOutCommand) {
        case Cancel cancel -> new Cancelled();
      };
    };
  }

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case OpenShoppingCartEvent __ -> {
        throw new IllegalStateException("Cannot apply event on a checkout out cart");
      }
      case CheckedOutShoppingCartEvent checkedOutEvent -> switch (checkedOutEvent) {
        case Cancelled cancelled -> new CheckedOutShoppingCart(cartId, items, true);
      };
    };
  }
}
