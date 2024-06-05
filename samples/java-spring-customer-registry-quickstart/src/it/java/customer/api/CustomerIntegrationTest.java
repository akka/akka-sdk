package customer.api;

import customer.domain.Address;
import customer.domain.Customer;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

  @Test
  public void create() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var res =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);


    var resUpdate =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::changeName)
          .invokeAsync("Katarina")
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

    Address address = new Address("Elm st. 5", "New Orleans");

    var res =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::changeAddress)
          .invokeAsync(address)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }

  private Customer getCustomerById(String customerId) {
    return await(
      componentClient
        .forValueEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
    );
  }

}
