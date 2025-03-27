package store.order.view.structured;

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

@ComponentId("structured-customer-orders")
public class StructuredCustomerOrdersView extends View {

  // tag::query[]
  @Query( // <1>
    """
      SELECT
       customers.customerId AS id,
       (name,
        address.street AS address1,
        address.city AS address2,
        email AS contactEmail) AS shipping,
       (products.productId AS id,
        productName AS name,
        quantity,
        (price.currency, price.units, price.cents) AS value,
        orderId,
        createdTimestamp AS orderCreatedTimestamp) AS orders
      FROM customers
      JOIN orders ON orders.customerId = customers.customerId
      JOIN products ON products.productId = orders.productId
      WHERE customers.customerId = :customerId
      ORDER BY orders.createdTimestamp
      """)
  public QueryEffect<StructuredCustomerOrders> get(String customerId) {
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
  public static class OrdersUpdater extends TableUpdater<Order> {
  }
}
