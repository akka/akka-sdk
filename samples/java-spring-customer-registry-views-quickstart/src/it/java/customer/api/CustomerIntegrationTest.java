package customer.api;


import com.google.protobuf.any.Any;
import customer.Main;
import customer.domain.Address;
import customer.domain.Customer;
import customer.view.CustomersByNameView;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.client.ComponentClient;
import static kalix.javasdk.testkit.DeferredCallSupport.execute;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Main.class)
public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

  @Autowired
  private WebClient webClient;

  @Autowired
  private ComponentClient componentClient;

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void create() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);


    var res =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    var resCreation =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

    var resUpdate =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::changeName)
          .params("Katarina")
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() throws InterruptedException {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    var resCreation =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);
    Address address = new Address("Elm st. 5", "New Orleans");

    var res =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::changeAddress)
          .params(address)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() throws Exception {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Foo", null);

    var resCreation =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);


    // the view is eventually updated
    await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
                webClient.get()
                    .uri("/customers/by_name/Foo")
                    .retrieve()
                    .bodyToFlux(CustomersByNameView.CustomerSummary.class)
                    .blockFirst(timeout)
                    .name(),
            new IsEqual("Foo")
        );
  }

  @Test
  public void findByEmail() throws Exception {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "bar@example.com", "Bar", null);

    var resCreation =
      execute(
        componentClient
          .forValueEntity(id)
          .call(CustomerEntity::create)
          .params(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

    // the view is eventually updated
    await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .until(() ->
                webClient.get()
                    .uri("/customer/by_email/bar@example.com")
                    .retrieve()
                    .bodyToMono(Customer.class)
                    .block(timeout)
                    .name(),
            new IsEqual("Bar")
        );
  }

  private Customer getCustomerById(String customerId) {
    return execute(
      componentClient
        .forValueEntity(customerId)
        .call(CustomerEntity::getCustomer)
    );
  }

}
