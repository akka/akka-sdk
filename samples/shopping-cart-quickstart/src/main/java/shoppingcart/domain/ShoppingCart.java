// tag::top[]
// tag::first-app-top-part-1[]
package shoppingcart.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
// end::first-app-top-part-1[]
// tag::first-app-top-part-2[]
import java.util.stream.Collectors;

// end::first-app-top-part-2[]
// end::top[]

// tag::all[]
// tag::domain[]

public record ShoppingCart(String cartId, List<LineItem> items, boolean checkedOut) { // <1>
  public record LineItem(String productId, String name, int quantity) { // <2>
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, name, quantity);
    }
  }

  // end::domain[]
  // tag::addItem[]
  public ShoppingCart addItem(LineItem item) {
    var lineItem = updateItem(item); // <1>
    List<LineItem> lineItems = removeItemByProductId(item.productId()); // <2>
    lineItems.add(lineItem); // <3>
    lineItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCart(cartId, lineItems, checkedOut); // <4>
  }

  private LineItem updateItem(LineItem item) {
    return findItemByProductId(item.productId())
      .map(li -> li.withQuantity(li.quantity() + item.quantity()))
      .orElse(item);
  }

  private List<LineItem> removeItemByProductId(String productId) {
    return items()
      .stream()
      .filter(lineItem -> !lineItem.productId().equals(productId))
      .collect(Collectors.toList());
  }

  public Optional<LineItem> findItemByProductId(String productId) {
    Predicate<LineItem> lineItemExists = lineItem -> lineItem.productId().equals(productId);
    return items.stream().filter(lineItemExists).findFirst();
  }

  // end::addItem[]

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
// end::all[]
