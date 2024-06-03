package customer;


import customer.api.CustomerEntity;
import customer.api.Ok;
import customer.domain.Address;
import customer.domain.Customer;
import kalix.javasdk.client.ComponentClient;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static kalix.javasdk.testkit.DeferredCallSupport.invokeAndAwait;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

  public record CustomersResponse(Collection<Customer> customers) { }

  @Autowired
  private ComponentClient componentClient;

  @Autowired
  private WebClient webClient;

  private Duration timeout = Duration.of(10, SECONDS);

  @Test
  public void create() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    addCustomer(customer);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  private Customer getCustomerById(String customerId) {
    return invokeAndAwait(
      componentClient
        .forValueEntity(customerId)
        .methodRef(CustomerEntity::getCustomer).deferred()
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

    await()
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
      invokeAndAwait(
        componentClient
          .forValueEntity(customer.customerId())
          .methodRef(CustomerEntity::create)
          .deferred(customer)
      );
    Assertions.assertEquals(Ok.instance, res);
  }
}
