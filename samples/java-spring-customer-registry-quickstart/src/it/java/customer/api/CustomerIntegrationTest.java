package customer.api;


import customer.Main;
import customer.domain.Address;
import customer.domain.Customer;
import kalix.javasdk.client.ComponentClient;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static kalix.javasdk.testkit.DeferredCallSupport.invokeAndAwait;

@SpringBootTest(classes = Main.class)
public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {


  @Autowired
  private ComponentClient componentClient;

  @Test
  public void create() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var res =
      invokeAndAwait(
        componentClient
          .forValueEntity(id)
          .methodRef(CustomerEntity::create)
          .deferred(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var resCreation =
      invokeAndAwait(
        componentClient
          .forValueEntity(id)
          .methodRef(CustomerEntity::create)
          .deferred(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);


    var resUpdate =
      invokeAndAwait(
        componentClient
          .forValueEntity(id)
          .methodRef(CustomerEntity::changeName)
          .deferred("Katarina")
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var resCreation =
      invokeAndAwait(
        componentClient
          .forValueEntity(id)
            .methodRef(CustomerEntity::create)
              .deferred(customer)
        );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

    Address address = new Address("Elm st. 5", "New Orleans");

    var res =
      invokeAndAwait(
        componentClient
          .forValueEntity(id)
          .methodRef(CustomerEntity::changeAddress)
          .deferred(address)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }

  private Customer getCustomerById(String customerId) {
    return invokeAndAwait(
      componentClient
        .forValueEntity(customerId)
        .methodRef(CustomerEntity::getCustomer).deferred()
    );
  }

}
