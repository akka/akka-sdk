package store.view.joined;

import akka.platform.javasdk.annotations.Query;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.annotations.Table;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.view.View;
import akka.platform.javasdk.view.TableUpdater;
import store.customer.api.CustomerEntity;
import store.customer.domain.CustomerEvent;
import store.order.api.OrderEntity;
import store.order.domain.Order;
import store.product.api.ProductEntity;
import store.product.domain.ProductEvent;
import store.view.QueryParameters;
import store.view.model.Customer;
import store.view.model.Product;

// tag::join[]
@ComponentId("joined-customer-orders") // <1>
public class JoinedCustomerOrdersView extends View {

  @Query( // <2>
    """
      SELECT * AS orders
      FROM customers
      JOIN orders ON customers.customerId = orders.customerId
      JOIN products ON products.productId = orders.productId
      WHERE customers.customerId = :customerId
      ORDER BY orders.createdTimestamp
      """)
  public QueryEffect<CustomerOrders> get(QueryParameters params) { // <3>
    return queryResult();
  }

  @Table("customers") // <4>
  @Consume.FromEventSourcedEntity(CustomerEntity.class)
  public static class Customers extends TableUpdater<Customer> {
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

  @Table("products") // <4>
  @Consume.FromEventSourcedEntity(ProductEntity.class)
  public static class Products extends TableUpdater<Product> {
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

  @Table("orders") // <4>
  @Consume.FromKeyValueEntity(OrderEntity.class)
  public static class Orders extends TableUpdater<Order> {
  }
}
// end::join[]