package shoppingcart.polymorphism.domain;

import shoppingcart.domain.ShoppingCart.LineItem;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.CheckedOutShoppingCartCommand;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.AddItem;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.Checkout;
import shoppingcart.polymorphism.domain.ShoppingCartCommand.OpenShoppingCartCommand.RemoveItem;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.CheckedOutShoppingCartEvent;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.OpenShoppingCartEvent;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.OpenShoppingCartEvent.CheckedOut;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.OpenShoppingCartEvent.ItemAdded;
import shoppingcart.polymorphism.domain.ShoppingCartEvent.OpenShoppingCartEvent.ItemRemoved;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record OpenShoppingCart(String cartId, List<LineItem> items) implements ShoppingCart {

  @Override
  public ShoppingCartEvent handleCommand(ShoppingCartCommand command) {
    return switch (command) {
      case CheckedOutShoppingCartCommand __ -> throw new IllegalStateException("Cannot handle command on an open cart");
      case OpenShoppingCartCommand openCommand -> switch (openCommand) {
        case AddItem addItem -> new ItemAdded(addItem.item());
        case RemoveItem removeItem -> new ItemRemoved(removeItem.productId());
        case Checkout checkout -> new CheckedOut();
      };
    };
  }

  @Override
  public ShoppingCart applyEvent(ShoppingCartEvent event) {
    return switch (event) {
      case CheckedOutShoppingCartEvent __ -> {
        throw new IllegalStateException("Cannot apply event on an open cart");
      }
      case OpenShoppingCartEvent openEvent -> switch (openEvent) {
        case CheckedOut checkedOut -> onCheckedOut();
        case ItemAdded itemAdded -> onItemAdded(itemAdded);
        case ItemRemoved itemRemoved -> onItemRemoved(itemRemoved);
      };
    };
  }


  public OpenShoppingCart onItemAdded(ItemAdded itemAdded) {
    var item = itemAdded.item();
    var lineItem = updateItem(item); // <1>
    List<LineItem> lineItems = removeItemByProductId(item.productId()); // <2>
    lineItems.add(lineItem); // <3>
    lineItems.sort(Comparator.comparing(LineItem::productId));
    return new OpenShoppingCart(cartId, lineItems); // <4>
  }

  private LineItem updateItem(LineItem item) {
    return findItemByProductId(item.productId())
      .map(li -> li.withQuantity(li.quantity() + item.quantity()))
      .orElse(item);
  }

  private List<LineItem> removeItemByProductId(String productId) {
    return items().stream()
      .filter(lineItem -> !lineItem.productId().equals(productId))
      .collect(Collectors.toList());
  }

  public Optional<LineItem> findItemByProductId(String productId) {
    Predicate<LineItem> lineItemExists =
      lineItem -> lineItem.productId().equals(productId);
    return items.stream().filter(lineItemExists).findFirst();
  }

  public OpenShoppingCart onItemRemoved(ItemRemoved itemRemoved) {
    List<LineItem> updatedItems =
      removeItemByProductId(itemRemoved.productId());
    updatedItems.sort(Comparator.comparing(LineItem::productId));
    return new OpenShoppingCart(cartId, updatedItems);
  }

  public ShoppingCart onCheckedOut() {
    return new CheckedOutShoppingCart(cartId, items, false);
  }
}
