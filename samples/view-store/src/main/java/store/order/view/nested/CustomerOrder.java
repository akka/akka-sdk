package store.order.view.nested;

import store.product.domain.Money;

public record CustomerOrder(
  String customerId,
  String orderId,
  String productId,
  String productName,
  Money price,
  int quantity,
  long createdTimestamp
) {}
