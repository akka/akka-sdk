package customer.api;

import akka.Done;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import customer.application.CustomerEntity;
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import customer.domain.Address;
import customer.domain.Customer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static org.assertj.core.api.Assertions.assertThat;


public class CustomerIntegrationTest extends TestKitSupport {

  @Test
  public void create() {
    String id = UUID.randomUUID().toString();
    var createCustomerRequest = new CustomerEndpoint.CreateCustomerRequest("foo@example.com", "Johanna", new Address("Regent Street", "London"));

    var response = httpClient.POST("/customer/" + id)
      .withRequestBody(createCustomerRequest)
      .invoke();
    Assertions.assertEquals(StatusCodes.CREATED, response.status());

    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void getUser() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street", "London")));

    var response = httpClient.GET("/customer/" + id)
      .responseBodyAs(Customer.class)
      .invoke();
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Johanna", response.body().name());
  }

  @Test
  public void getNonexistantUser() {
    String id = UUID.randomUUID().toString();

    // FIXME invoke async throws on error codes, runtime ex, no way to inspect http response #2879
    Assertions.assertThrows(RuntimeException.class, () ->
      httpClient.GET("/customer/" + id)
        .responseBodyAs(Customer.class)
        .invoke()
    );
  }

  @Test
  public void changeName() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street", "London")));

    httpClient.PATCH("/customer/" + id + "/name/Katarina").invoke();

    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street", "London")));

    var newAddress = new Address("Elm st. 5", "New Orleans");
    var response = httpClient.PATCH("/customer/" + id + "/address")
      .withRequestBody(newAddress)
      .invoke();
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() {
    String id = UUID.randomUUID().toString();
    createCustomer(id, new Customer("foo@example.com", "Foo", new Address("Regent Street", "London")));

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var customerName = componentClient.forView()
          .method(CustomersByNameView::getCustomers)
          .invoke("Foo")
          .customers().stream().findFirst().get().name();

        assertThat(customerName).isEqualTo("Foo");
      });
  }

  @Test
  public void findByEmail() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("bar@example.com", "Bar", new Address("Regent Street", "London"));
    Done response =
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invoke(customer);

    Assertions.assertEquals(done(), response);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var customerName = componentClient.forView()
          .method(CustomersByEmailView::getCustomers)
          .invoke("bar@example.com")
          .customers().stream().findFirst().get().name();

        assertThat(customerName).isEqualTo("Bar");
      });
  }

  private void createCustomer(String id, Customer customer) {
    componentClient.forEventSourcedEntity(id)
      .method(CustomerEntity::create)
      .invoke(customer);
  }

  private Customer getCustomerById(String id) {
    return
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::getCustomer)
        .invoke();
  }

}
