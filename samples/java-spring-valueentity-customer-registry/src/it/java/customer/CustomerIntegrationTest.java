package customer;


import customer.api.CustomerEntity;
import customer.api.Ok;
import customer.domain.Address;
import customer.domain.Customer;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

  public record CustomersResponse(Collection<Customer> customers) { }

  private Duration timeout = Duration.of(10, SECONDS);

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
        .forValueEntity(customerId)
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
          CustomersResponse response = webClient
              .get()
              .uri("/wrapped/by_city?cities=Nazare&cities=Lisbon")
              .retrieve()
              .bodyToMono(CustomersResponse.class)
              .block(timeout);

          assertThat(response.customers).containsOnly(johanna, joe);
        });
  }

  private void addCustomer(Customer customer) {

    var res =
      await(
        componentClient
          .forValueEntity(customer.customerId())
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(Ok.instance, res);
  }
}
