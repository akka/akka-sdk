package shoppingcart.domain;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

// tag::domain[]
public record ShoppingCartState(String cartId, List<LineItem> items, boolean checkedOut) {

  public record LineItem(String productId, int quantity) {
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, quantity);
    }
  }

  // end::domain[]

  public ShoppingCartState onItemAdded(ShoppingCartEvent.ItemAdded itemAdded) {
    var item = new LineItem(itemAdded.productId(), itemAdded.quantity());
    var lineItem = updateItem(item);
    List<LineItem> lineItems = removeItemByProductId(item.productId());
    lineItems.add(lineItem);
    lineItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCartState(cartId, lineItems, checkedOut);
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
    Predicate<LineItem> lineItemExists = lineItem -> lineItem.productId().equals(productId);
    return items.stream().filter(lineItemExists).findFirst();
  }

  public ShoppingCartState onItemRemoved(ShoppingCartEvent.ItemRemoved itemRemoved) {
    List<LineItem> updatedItems = removeItemByProductId(itemRemoved.productId());
    updatedItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCartState(cartId, updatedItems, checkedOut);
  }

  public ShoppingCartState onCheckedOut() {
    return new ShoppingCartState(cartId, items, true);
  }
  // tag::domain[]
}
// end::domain[]
