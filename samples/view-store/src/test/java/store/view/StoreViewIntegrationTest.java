package store.view;

import akka.javasdk.testkit.TestKitSupport;
import store.customer.application.CustomerEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.order.application.CreateOrder;
import store.order.application.OrderEntity;
import store.product.application.ProductEntity;
import store.product.domain.Money;
import store.product.domain.Product;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class StoreViewIntegrationTest extends TestKitSupport {

  protected void createProduct(String id, String name, String currency, long units, int cents) {
    Product product = new Product(name, new Money(currency, units, cents));
    var response =
      await(
        componentClient
          .forEventSourcedEntity(id)
          .method(ProductEntity::create)
          .invokeAsync(product));
    assertNotNull(response);
  }

  protected void changeProductName(String id, String newName) {
    var response =
      await(
        componentClient
          .forEventSourcedEntity(id)
          .method(ProductEntity::changeName)
          .invokeAsync(newName));
    assertNotNull(response);
  }

  protected void createCustomer(String id, String email, String name, String street, String city) {
    Customer customer = new Customer(email, name, new Address(street, city));
    var response =
      await(
        componentClient
          .forEventSourcedEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    assertNotNull(response);
  }

  protected void changeCustomerName(String id, String newName) {
    var response =
      await(
        componentClient
          .forEventSourcedEntity(id)
          .method(CustomerEntity::changeName)
          .invokeAsync(newName)
      );
    assertNotNull(response);
  }

  protected void createOrder(String id, String productId, String customerId, int quantity) {
    CreateOrder createOrder = new CreateOrder(productId, customerId, quantity);
    var response =
      await(
        componentClient
          .forKeyValueEntity(id)
          .method(OrderEntity::create)
          .invokeAsync(createOrder)
      );
    assertNotNull(response);
  }

}
