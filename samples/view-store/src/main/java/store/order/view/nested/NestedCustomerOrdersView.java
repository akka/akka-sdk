package store.order.view.nested;

import akka.javasdk.annotations.Query;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Table;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import store.customer.application.CustomerEntity;
import store.customer.domain.CustomerEvent;
import store.order.application.OrderEntity;
import store.order.domain.Order;
import store.product.application.ProductEntity;
import store.product.domain.ProductEvent;
import store.order.view.model.Customer;
import store.order.view.model.Product;

@ComponentId("nested-customer-orders")
public class NestedCustomerOrdersView extends View {

  // tag::query[]
  @Query( // <1>
    """
      SELECT customers.*, (orders.*, products.*) AS orders
      FROM customers
      JOIN orders ON customers.customerId = orders.customerId
      JOIN products ON products.productId = orders.productId
      WHERE customers.customerId = :customerId
      ORDER BY orders.createdTimestamp
      """)
  public QueryEffect<NestedCustomerOrders> get(String customerId) { // <2>
    return queryResult();
  }
  // end::query[]

  @Table("customers")
  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class CustomersUpdater extends TableUpdater<Customer> {
    public Effect<Customer> onEvent(CustomerEvent event) {
      return switch (event) {
        case CustomerEvent.CustomerCreated created -> {
          String id = updateContext().eventSubject().orElse("");
          yield effects()
            .updateRow(new Customer(id, created.email(), created.name(), created.address()));
        }

        case CustomerEvent.CustomerNameChanged nameChanged ->
          effects().updateRow(rowState().withName(nameChanged.newName()));

        case CustomerEvent.CustomerAddressChanged addressChanged ->
          effects().updateRow(rowState().withAddress(addressChanged.newAddress()));
      };
    }
  }

  @Table("products")
  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class ProductsUpdater extends TableUpdater<Product> {
    public Effect<Product> onEvent(ProductEvent event) {
      return switch (event) {
        case ProductEvent.ProductCreated created -> {
          String id = updateContext().eventSubject().orElse("");
          yield effects().updateRow(new Product(id, created.name(), created.price()));
        }

        case ProductEvent.ProductNameChanged nameChanged ->
          effects().updateRow(rowState().withProductName(nameChanged.newName()));

        case ProductEvent.ProductPriceChanged priceChanged ->
          effects().updateRow(rowState().withPrice(priceChanged.newPrice()));
      };
    }
  }

  @Table("orders")
  @Consume.FromKeyValueEntity(OrderEntity.class)
  public static class OrdersUpdater extends TableUpdater<Order> { }
}
