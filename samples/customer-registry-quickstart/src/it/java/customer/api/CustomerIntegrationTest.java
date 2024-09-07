package customer.api;

import akka.javasdk.testkit.TestKitSupport;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class CustomerIntegrationTest extends TestKitSupport {

  @Test
  public void create() {
    var id = newUniqueId();
    var customer = new Customer(id, "foo@example.com", "Johanna", null);

    var response = await(httpClient.POST("/customer")
        .withRequestBody(customer)
        .responseBodyAs(CustomerEntity.Ok.class)
        .invokeAsync());

    Assertions.assertEquals(CustomerEntity.Ok.instance, response.body());
    Assertions.assertEquals("Johanna", getCustomerFromEntity(id).name());
  }

  @Test
  public void changeName() {
    var id = newUniqueId();
    createCustomerEntity(new Customer(id, "foo@example.com", "Johanna", null));

    var response =
      await(
        httpClient.PUT("/customer/" + id + "/name")
          .withRequestBody("Katarina")
          .responseBodyAs(CustomerEntity.Ok.class)
          .invokeAsync()
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, response.body());
    Assertions.assertEquals("Katarina", getCustomerFromEntity(id).name());
  }

  @Test
  public void changeAddress() {
    var id = newUniqueId();
    createCustomerEntity(new Customer(id, "foo@example.com", "Johanna", null));

    var address = new Address("Elm st. 5", "New Orleans");

    var response =
      await(
          httpClient.PUT("/customer/" + id + "/address")
              .withRequestBody(address)
              .responseBodyAs(CustomerEntity.Ok.class)
              .invokeAsync()
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, response.body());
    Assertions.assertEquals("Elm st. 5", getCustomerFromEntity(id).address().street());
  }

  // access the entity directly
  private void createCustomerEntity(Customer customer) {
    var creationResponse =
        await(
            componentClient
                .forKeyValueEntity(customer.customerId())
                .method(CustomerEntity::create)
                .invokeAsync(customer)
        );
    Assertions.assertEquals(CustomerEntity.Ok.instance, creationResponse);
  }

  private Customer getCustomerFromEntity(String customerId) {
    return await(
      componentClient
        .forKeyValueEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
    );
  }

  private static String newUniqueId() {
    return UUID.randomUUID().toString();
  }

}
