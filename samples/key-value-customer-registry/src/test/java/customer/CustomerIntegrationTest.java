package customer;


import akka.Done;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import customer.application.CustomerEntity;
import customer.application.CustomerList;
import customer.application.CustomersByCity;
import customer.application.CustomersByEmail;
import customer.application.CustomersByName;
import customer.domain.Address;
import customer.domain.Customer;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class CustomerIntegrationTest extends TestKitSupport {

  @Test
  public void create()  {
    String id = newUniqueId();
    Customer customer = new Customer("foo@example.com", "Johanna", new Address("Some Street", "Somewhere"));

    createCustomer(id, customer);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  private Customer getCustomerById(String customerId) {
    return
      componentClient
        .forKeyValueEntity(customerId)
        .method(CustomerEntity::getCustomer).invoke();
  }

  @Test
  public void httpCreate() {
    var id = newUniqueId();
    var customer = new Customer("foo@example.com", "Johanna", new Address("Some Street", "Somewhere"));

    var response = httpClient.POST("/customer/" + id)
        .withRequestBody(customer)
        .invoke();

    Assertions.assertEquals(StatusCodes.CREATED, response.status());
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void httpChangeName() {
    var id = newUniqueId();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Some Street", "Somewhere")));

    var response = httpClient.PATCH("/customer/" + id + "/name/Katarina").invoke();
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void httpChangeAddress() {
    var id = newUniqueId();
    createCustomer(id, new Customer("foo@example.com", "Johanna", new Address("Regent Street","London")));

    var newAddress = new Address("Elm st. 5", "New Orleans");
    var response = httpClient.PATCH("/customer/" + id + "/address")
        .withRequestBody(newAddress)
        .invoke();
    Assertions.assertEquals(StatusCodes.OK, response.status());
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }

  @Test
  public void findByCity() {
    Customer johanna = new Customer("johanna@example.com", "Johanna", new Address("Cool Street", "Nazare"));
    Customer joe = new Customer("joe@example.com", "Joe", new Address("Cool Street", "Lisbon"));
    Customer jane = new Customer("jane@example.com", "Jane", new Address("Cool Street", "Faro"));

    createCustomer(newUniqueId(), johanna);
    createCustomer(newUniqueId(),joe);
    createCustomer(newUniqueId(), jane);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          CustomerList response =

              componentClient
                .forView()
                .method(CustomersByCity::getCustomers)
                .invoke(List.of("Nazare", "Lisbon")
            );
          assertThat(response.customers()).containsOnly(johanna, joe);
        });
  }

  @Test
  public void findByName() throws Exception {
    var id = newUniqueId();
    createCustomer(id, new Customer("foo@example.com", "Foo", new Address("Some Street", "Somewhere")));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
            componentClient.forView()
                .method(CustomersByName::getCustomers)
                .invoke("Foo").customers().stream().findFirst().get().name(),
            new IsEqual("Foo")
        );
  }

  @Test
  public void findByEmail() throws Exception {
    String id = newUniqueId();
    createCustomer(id, new Customer("bar@example.com", "Bar", new Address("Some Street", "Somewhere")));

    // the view is eventually updated
    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var foundCustomers =
              componentClient.forView()
                  .method(CustomersByEmail::getCustomer)
                  .invoke("bar@example.com");

          Assertions.assertEquals(1, foundCustomers.customers().size());
          Assertions.assertEquals("Bar", foundCustomers.customers().getFirst().name());
        });
  }

  private void createCustomer(String id, Customer customer) {
    var res =

        componentClient
          .forKeyValueEntity(id)
          .method(CustomerEntity::create)
          .invoke(customer);
    Assertions.assertEquals(Done.done(), res);
  }

  private static String newUniqueId() {
    return UUID.randomUUID().toString();
  }
}
