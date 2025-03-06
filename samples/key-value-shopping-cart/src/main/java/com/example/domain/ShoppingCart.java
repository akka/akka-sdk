package com.example.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record ShoppingCart(String cartId, List<LineItem> items, Instant creationTimestamp) {

  public record LineItem(String productId, String name, int quantity) {
    public LineItem withQuantity(int quantity) {
      return new LineItem(productId, name, quantity);
    }
  }

  public ShoppingCart withItem(LineItem itemAdded) {
    var lineItem = updateItem(itemAdded); // <1>
    List<LineItem> lineItems =
      removeItemByProductId(itemAdded.productId()); // <2>
    lineItems.add(lineItem); // <3>
    lineItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCart(cartId, lineItems, creationTimestamp); // <4>
  }

  public ShoppingCart withoutItem(LineItem itemRemoved) {
    List<LineItem> updatedItems =
      removeItemByProductId(itemRemoved.productId());
    updatedItems.sort(Comparator.comparing(LineItem::productId));
    return new ShoppingCart(cartId, updatedItems, creationTimestamp);
  }

  public ShoppingCart withCreationTimestamp(Instant ts) {
    return new ShoppingCart(cartId, items, ts);
  }

  private List<LineItem> removeItemByProductId(String productId) {
    return items().stream()
      .filter(lineItem -> !lineItem.productId().equals(productId))
      .collect(Collectors.toList());
  }

  private LineItem updateItem(LineItem item) {
    return findItemByProductId(item.productId())
      .map(li -> li.withQuantity(li.quantity() + item.quantity()))
      .orElse(item);
  }

  public Optional<LineItem> findItemByProductId(String productId) {
    Predicate<LineItem> lineItemExists =
      lineItem -> lineItem.productId().equals(productId);
    return items.stream().filter(lineItemExists).findFirst();
  }

  public static ShoppingCart of(String id) {
    return new ShoppingCart(id, Collections.emptyList(), Instant.EPOCH);
  }
}
