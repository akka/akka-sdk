package store.order.view.nested;

import java.util.List;
import store.customer.domain.Address;

public record NestedCustomerOrders(
  String customerId,
  String email,
  String name,
  Address address,
  List<CustomerOrder> orders
) {}
