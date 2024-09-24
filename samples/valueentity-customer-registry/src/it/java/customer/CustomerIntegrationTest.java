package customer;


import akka.Done;
import akka.javasdk.testkit.TestKitSupport;
import customer.application.CustomerEntity;
import customer.application.CustomerList;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomersByCity;
import org.awaitility.Awaitility;
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
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    addCustomer(customer);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  private Customer getCustomerById(String customerId) {
    return await(
      componentClient
        .forKeyValueEntity(customerId)
        .method(CustomerEntity::getCustomer).invokeAsync()
    );
  }

  @Test
  public void searchByCity() {
    Customer johanna = new Customer(UUID.randomUUID().toString(), "johanna@example.com", "Johanna", new Address("Cool Street", "Nazare"));
    Customer joe = new Customer(UUID.randomUUID().toString(), "joe@example.com", "Joe", new Address("Cool Street", "Lisbon"));
    Customer jane = new Customer(UUID.randomUUID().toString(), "jane@example.com", "Jane", new Address("Cool Street", "Faro"));

    addCustomer(johanna);
    addCustomer(joe);
    addCustomer(jane);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          CustomerList response =
            await(
              componentClient
                .forView()
                .method(CustomersByCity::getCustomers)
                .invokeAsync(List.of("Nazare", "Lisbon"))
            );
          assertThat(response.customers()).containsOnly(johanna, joe);
        });
  }

  private void addCustomer(Customer customer) {
    var res =
      await(
        componentClient
          .forKeyValueEntity(customer.customerId())
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(Done.done(), res);
  }
}
