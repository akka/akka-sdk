package store.view;

import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import store.customer.api.CustomerEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.order.api.CreateOrder;
import store.order.api.OrderEntity;
import store.product.api.ProductEntity;
import store.product.domain.Money;
import store.product.domain.Product;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public abstract class StoreViewIntegrationTest extends KalixIntegrationTestKitSupport {

  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT.withAdvancedViews();
  }

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
