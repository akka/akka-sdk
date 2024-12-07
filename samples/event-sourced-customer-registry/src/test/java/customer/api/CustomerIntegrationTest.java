package customer.api;

import akka.Done;
import akka.http.javadsl.model.StatusCodes;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerByEmailView;
import customer.application.CustomerByNameView;
import akka.javasdk.testkit.TestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;


public class CustomerIntegrationTest extends TestKitSupport {

  @Test
  public void create() {
    String id = UUID.randomUUID().toString();
    var createCustomerRequest = new CustomerEndpoint.CreateCustomerRequest("foo@example.com", "Johanna", new Address("Regent Street","London"));

    var response = await(httpClient.POST("/customer/" + id)
        .withRequestBody(createCustomerRequest)
        .invokeAsync());
    Assertions.assertEquals(StatusCodes.CREATED, response.status());

    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void getUser() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street","London")));

    var response = await(httpClient.GET("/customer/" + id)
        .responseBodyAs(Customer.class)
        .invokeAsync());
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Johanna", response.body().name());
  }

  @Test
  public void getNonexistantUser() {
    String id = UUID.randomUUID().toString();

    // FIXME invoke async throws on error codes, runtime ex, no way to inspect http response #2879
    Assertions.assertThrows(RuntimeException.class, () ->
        await(httpClient.GET("/customer/" + id)
        .responseBodyAs(Customer.class)
        .invokeAsync())
    );
  }

  @Test
  public void changeName() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street","London")));

    await(httpClient.PATCH("/customer/" + id + "/name/Katarina").invokeAsync());

    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street","London")));

    var newAddress = new Address("Elm st. 5", "New Orleans");
    var response = await(httpClient.PATCH("/customer/" + id + "/address")
        .withRequestBody(newAddress)
        .invokeAsync());
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Foo", new Address("Regent Street","London")));

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() ->
        await(
          componentClient.forView()
            .method(CustomerByNameView::getCustomers)
            .invokeAsync("Foo")
        ).customers().stream().findFirst().get().name(),
        new IsEqual("Foo")
      );
  }

  @Test
  public void findByEmail() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("bar@example.com", "Bar", new Address("Regent Street","London"));
    Done response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(done(), response);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() ->
          await(
            componentClient.forView()
              .method(CustomerByEmailView::getCustomers)
              .invokeAsync("bar@example.com")
          ).customers().stream().findFirst().get().name(),
        new IsEqual("Bar")
      );
  }

  private void createCustomer(String id, Customer customer) {
    await(
        componentClient.forEventSourcedEntity(id)
            .method(CustomerEntity::create)
            .invokeAsync(customer));
  }

  private Customer getCustomerById(String id) {
    return await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::getCustomer)
        .invokeAsync());
  }

}
