package customer.api;

import akka.Done;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.application.CustomerByEmailView;
import customer.application.CustomerByNameView;
import akka.javasdk.testkit.AkkaSdkTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static akka.Done.done;
import static java.time.temporal.ChronoUnit.SECONDS;


public class CustomerIntegrationTest extends AkkaSdkTestKitSupport {

  private Duration timeout = Duration.of(5, SECONDS);

  @Test
  public void create() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Done response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(done(), response);
    Assertions.assertEquals("Johanna", getCustomerById(id).name());
  }

  @Test
  public void changeName() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Done response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(done(), response);

    Done resUpdate = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::changeName)
        .invokeAsync("Katarina"));


    Assertions.assertEquals(done(), resUpdate);
    Assertions.assertEquals("Katarina", getCustomerById(id).name());
  }

  @Test
  public void changeAddress() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Johanna", null);

    Done response = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::create)
        .invokeAsync(customer));

    Assertions.assertEquals(done(), response);

    Address address = new Address("Elm st. 5", "New Orleans");

    Done resUpdate = await(
      componentClient.forEventSourcedEntity(id)
        .method(CustomerEntity::changeAddress)
        .invokeAsync(address));

    Assertions.assertEquals(done(), resUpdate);
    Assertions.assertEquals("Elm st. 5", getCustomerById(id).address().street());
  }


  @Test
  public void findByName() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("foo@example.com", "Foo", null);
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
            .method(CustomerByNameView::getCustomers)
            .invokeAsync(new CustomerByNameView.QueryParameters("Foo"))
        ).customers().stream().findFirst().get().name(),
        new IsEqual("Foo")
      );
  }

  @Test
  public void findByEmail() {
    String id = UUID.randomUUID().toString();
    Customer customer = new Customer("bar@example.com", "Bar", null);
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
