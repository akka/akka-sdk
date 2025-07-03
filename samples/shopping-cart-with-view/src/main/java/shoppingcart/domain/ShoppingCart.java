package shoppingcart.domain;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

// tag::domain[]
public record ShoppingCart(String cartId, List<LineItem> items, boolean checkedOut) {

  public record LineItem(String productId, int quantity) {
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, quantity);
    }
  }

  // end::domain[]

  public ShoppingCart onItemAdded(LineItem item) {
    var lineItem = updateItem(item);
    List<LineItem> lineItems = removeItemByProductId(item.productId());
    lineItems.add(lineItem);
    lineItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCart(cartId, lineItems, checkedOut);
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

  public ShoppingCart removeItem(String productId) {
    List<LineItem> updatedItems = removeItemByProductId(productId);
    updatedItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCart(cartId, updatedItems, checkedOut);
  }

  public ShoppingCart onCheckedOut() {
    return new ShoppingCart(cartId, items, true);
  }
  // tag::domain[]
}
// end::domain[]
