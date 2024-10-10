package customer.api;


import akka.javasdk.testkit.TestKitSupport;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerEntity;
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CustomerIntegrationTest extends TestKitSupport {

  @Test
  public void create() {
    var id = newUniqueId();
    var customer = new Customer("foo@example.com", "Johanna", null);

    var response = await(httpClient.POST("/customer/" + id)
        .withRequestBody(customer)
        .responseBodyAs(CustomerEntity.Ok.class)
        .invokeAsync());

    Assertions.assertEquals(CustomerEntity.Ok.instance, response.body());
    Assertions.assertEquals("Johanna", getCustomerFromEntity(id).name());
  }

  @Test
  public void changeName() {
    var id = newUniqueId();
    createCustomerEntity(id, new Customer("foo@example.com", "Johanna", null));

    var response =
        await(
            httpClient.PUT("/customer/" + id + "/name")
                // FIXME string request body https://github.com/lightbend/kalix-runtime/issues/2635
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
    createCustomerEntity(id, new Customer("foo@example.com", "Johanna", null));

    Address address = new Address("Elm st. 5", "New Orleans");

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


  @Test
  public void findByName() throws Exception {
    var id = newUniqueId();
    createCustomerEntity(id, new Customer("foo@example.com", "Foo", null));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
      .until(() ->
          await(
            componentClient.forView()
              .method(CustomersByNameView::getCustomers)
              .invokeAsync("Foo")
          ).customers().stream().findFirst().get().name(),
            new IsEqual("Foo")
        );
  }

  @Test
  public void findByEmail() throws Exception {
    String id = newUniqueId();
    createCustomerEntity(id, new Customer("bar@example.com", "Bar", null));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var foundCustomers = await(
              componentClient.forView()
                  .method(CustomersByEmailView::getCustomer)
                  .invokeAsync("bar@example.com")
          );

          Assertions.assertEquals(1, foundCustomers.customers().size());
          Assertions.assertEquals("Bar", foundCustomers.customers().getFirst().name());
        });
  }

  // access the entity directly
  private void createCustomerEntity(String id, Customer customer) {
    var creationResponse =
        await(
            componentClient
                .forKeyValueEntity(id)
                .method(CustomerEntity::create)
                .invokeAsync(customer)
        );
    Assertions.assertEquals(CustomerEntity.Ok.instance, creationResponse);
  }

  private Customer getCustomerFromEntity(String id) {
    return await(
      componentClient
        .forKeyValueEntity(id)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
    );
  }

  private static String newUniqueId() {
    return UUID.randomUUID().toString();
  }

}
