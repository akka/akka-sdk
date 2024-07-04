package customer.api;


import customer.domain.Address;
import customer.domain.Customer;
import customer.view.CustomerByEmailView;
import customer.view.CustomersByNameView;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;

public class CustomerIntegrationTest extends KalixIntegrationTestKitSupport {

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void create() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);


    var res =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName()  {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

    var resUpdate =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::changeName)
          .invokeAsync("Katarina")
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress()  {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Johanna", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);
    Address address = new Address("Elm st. 5", "New Orleans");

    var res =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::changeAddress)
          .invokeAsync(address)
      );

    Assertions.assertEquals(CustomerEntity.Ok.instance, res);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() throws Exception {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "foo@example.com", "Foo", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);


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
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer(id, "bar@example.com", "Bar", null);

    var resCreation =
      await(
        componentClient
          .forValueEntity(id)
          .method(CustomerEntity::create)
          .invokeAsync(customer)
      );
    Assertions.assertEquals(CustomerEntity.Ok.instance, resCreation);

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

  private Customer getCustomerById(String customerId) {
    return await(
      componentClient
        .forValueEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync()
    );
  }

}
