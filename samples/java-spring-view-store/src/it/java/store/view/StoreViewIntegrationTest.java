package store.view;

import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.client.ComponentClient;
import static kalix.javasdk.testkit.DeferredCallSupport.execute;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import store.Main;
import store.customer.api.CustomerEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.order.api.CreateOrder;
import store.order.api.OrderEntity;
import store.product.api.ProductEntity;
import store.product.domain.Money;
import store.product.domain.Product;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Import(TestKitConfig.class)
@SpringBootTest(classes = Main.class)
@DirtiesContext // fresh testkit and proxy for each integration test
public abstract class StoreViewIntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private ComponentClient componentClient;

  protected void createProduct(String id, String name, String currency, long units, int cents) {
    Product product = new Product(name, new Money(currency, units, cents));
    var response =
      execute(
        componentClient
          .forEventSourcedEntity(id)
          .call(ProductEntity::create)
          .params(product));
    assertNotNull(response);
  }

  protected void changeProductName(String id, String newName) {
    var response =
      execute(
        componentClient
          .forEventSourcedEntity(id)
          .call(ProductEntity::changeName)
          .params(newName));
    assertNotNull(response);
  }

  protected void createCustomer(String id, String email, String name, String street, String city) {
    Customer customer = new Customer(email, name, new Address(street, city));
    var response =
      execute(
        componentClient
          .forEventSourcedEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );
    assertNotNull(response);
  }

  protected void changeCustomerName(String id, String newName) {
    var response =
      execute(
        componentClient
          .forEventSourcedEntity(id)
          .call(CustomerEntity::changeName)
          .params(newName)
      );
    assertNotNull(response);
  }

  protected void createOrder(String id, String productId, String customerId, int quantity) {
    CreateOrder createOrder = new CreateOrder(productId, customerId, quantity);
    var response =
      execute(
        componentClient
          .forValueEntity(id)
          .call(OrderEntity::create)
          .params(createOrder)
      );
    assertNotNull(response);
  }

}
