package customer.application;

import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import customer.domain.Address;
import customer.domain.Customer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// tag::view-test[]

class CustomersByCityIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withKeyValueEntityIncomingMessages("customer"); // <1>
  }

  @Test
  public void shouldGetCustomerByCity() {
    IncomingMessages customerEvents = testKit.getKeyValueEntityIncomingMessages("customer"); // <2>

    Customer johanna = new Customer("johanna@example.com", "Johanna",
      new Address("Cool Street", "Porto"));
    Customer bob = new Customer("boc@example.com", "Bob",
      new Address("Baker Street", "London"));
    Customer alice = new Customer("alice@example.com", "Alice",
      new Address("Long Street", "Wroclaw"));


    customerEvents.publish(johanna, "1"); // <3>
    customerEvents.publish(bob, "2");
    customerEvents.publish(alice, "3");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {

          CustomerList customersResponse =
            componentClient.forView()
              .method(CustomersByCity::getCustomers) // <4>
              .invoke(List.of("Porto", "London"));

          assertThat(customersResponse.customers()).containsOnly(johanna, bob);
        }
      );
  }
  // end::view-test[]

  @Test
  public void shouldGetCustomerByCityAndName() {
    IncomingMessages customerEvents = testKit.getKeyValueEntityIncomingMessages("customer");

    Customer johanna = new Customer("johanna@example.com", "Johanna",
      new Address("Cool Street", "London"));
    Customer bob = new Customer("boc@example.com", "Bob",
      new Address("Baker Street", "London"));
    Customer alice = new Customer("alice@example.com", "Alice",
      new Address("Long Street", "Wroclaw"));


    customerEvents.publish(johanna, "1");
    customerEvents.publish(bob, "2");
    customerEvents.publish(alice, "3");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {

          Customer customer =
            componentClient.forView()
              .method(CustomersByCity::getCustomersByCityAndName)
              .invoke(new CustomersByCity.QueryParams("Johanna", "London"));

          assertThat(customer).isEqualTo(johanna);
        }
      );
  }
  // tag::view-test[]
}
// end::view-test[]
