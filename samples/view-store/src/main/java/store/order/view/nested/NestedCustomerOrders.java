package store.order.view.nested;

import store.customer.domain.Address;

import java.util.List;

// tag::nested[]
public record NestedCustomerOrders(
  String customerId,
  String email,
  String name,
  Address address,
  List<CustomerOrder> orders) {
} // <1>
// end::nested[]
