package store.order.view.structured;

import java.util.List;

public record StructuredCustomerOrders(
  String id,
  CustomerShipping shipping,
  List<ProductOrder> orders
) {}
