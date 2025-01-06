package store.order.view.structured;

import java.util.List;

// tag::structured[]
public record StructuredCustomerOrders(
  String id,
  CustomerShipping shipping,
  List<ProductOrder> orders) {
}
// end::structured[]
