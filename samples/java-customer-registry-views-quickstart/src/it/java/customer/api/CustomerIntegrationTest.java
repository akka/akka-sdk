package customer.api;


import akka.http.javadsl.model.ContentTypes;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerEntity;
import customer.application.CustomerByEmailView;
import customer.application.CustomersByNameView;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

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
                // FIXME string request body https://github.com/lightbend/kalix-runtime/issues/2635
                .withRequestBody(ContentTypes.APPLICATION_JSON, "\"Katarina\"".getBytes(StandardCharsets.UTF_8))
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
    createCustomerEntity(new Customer(id, "foo@example.com", "Foo", null));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
      .until(() ->
          await(
            componentClient.forView()
              .method(CustomersByNameView::getCustomers)
              .invokeAsync(new CustomersByNameView.QueryParameters("Foo"))
          ).customers().stream().findFirst().get().name(),
            new IsEqual("Foo")
        );
  }

  @Test
  public void findByEmail() throws Exception {
    String id = newUniqueId();
    createCustomerEntity(new Customer(id, "bar@example.com", "Bar", null));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
            await(
              componentClient.forView()
                .method(CustomerByEmailView::getCustomer)
                .invokeAsync(new CustomerByEmailView.QueryParameters("bar@example.com"))
            ).name(),
            new IsEqual("Bar")
        );
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
