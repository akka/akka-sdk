package store.order.view.structured;

public record ProductOrder(
  String id,
  String name,
  int quantity,
  ProductValue value,
  String orderId,
  long orderCreatedTimestamp
) {}
