package store.order.view.joined;

import store.customer.domain.Address;
import store.product.domain.Money;

public record CustomerOrder(
  String orderId,
  String productId,
  String productName,
  Money price,
  int quantity,
  String customerId,
  String email,
  String name,
  Address address,
  long createdTimestamp
) {}

