package customer.api;

import customer.api.CustomerEntity.Confirm;
import customer.domain.Address;
import customer.domain.Customer;
import customer.view.CustomerByEmailView;
import customer.view.CustomerByNameView;
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
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Confirm response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(Confirm.done, response);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Confirm response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(Confirm.done, response);

    Confirm resUpdate = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::changeName)
        .invokeAsync("Katarina"));


    Assertions.assertEquals(Confirm.done, resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Confirm response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(Confirm.done, response);

    Address address = new Address("Elm st. 5", "New Orleans");

    Confirm resUpdate = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::changeAddress)
        .invokeAsync(address));

    Assertions.assertEquals(Confirm.done, resUpdate);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Foo", null);
    Confirm response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(Confirm.done, response);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() ->
        await(
          componentClient.forView()
            .method(CustomerByNameView::getCustomer)
            .invokeAsync(new CustomerByNameView.QueryParameters("Foo"))
        ).customers().stream().findFirst().get().name(),
        new IsEqual("Foo")
      );
  }

  @Test
  public void findByEmail() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("bar@example.com", "Bar", null);
    Confirm response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(Confirm.done, response);

    // the view is eventually updated
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .until(() ->
          await(
            componentClient.forView()
              .method(CustomerByEmailView::getCustomer)
              .invokeAsync(new CustomerByEmailView.QueryParameters("bar@example.com"))
          ).customers().stream().findFirst().get().name(),
        new IsEqual("Bar")
      );
  }

  private Customer getCustomerById(String customerId) {
    return await(
      componentClient.forEventSourcedEntity(customerId)
        .method(CustomerEntity::getCustomer)
        .invokeAsync());
  }

}
