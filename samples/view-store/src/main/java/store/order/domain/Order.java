package store.order.domain;

public record Order(
  String orderId,
  String productId,
  String customerId,
  int quantity,
  long createdTimestamp
) {}
