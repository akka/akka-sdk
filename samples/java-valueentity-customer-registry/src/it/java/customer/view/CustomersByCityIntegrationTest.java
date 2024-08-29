package customer.view;

import customer.api.CustomerList;
import customer.domain.Address;
import customer.domain.Customer;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.AkkaSdkTestKit;
import akka.javasdk.testkit.AkkaSdkTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

// tag::view-test[]

class CustomersResponseByCityIntegrationTest extends AkkaSdkTestKitSupport {

  @Override
  protected AkkaSdkTestKit.Settings kalixTestKitSettings() {
    return AkkaSdkTestKit.Settings.DEFAULT
            .withKeyValueEntityIncomingMessages("customer"); // <1>
  }

  @Test
  public void shouldGetCustomerByCity() {
    IncomingMessages customerEvents = akkaSdkTestKit.getKeyValueEntityIncomingMessages("customer"); // <2>

    Customer johanna = new Customer("1", "johanna@example.com", "Johanna",
      new Address("Cool Street", "Porto"));
    Customer bob = new Customer("2", "boc@example.com", "Bob",
      new Address("Baker Street", "London"));
    Customer alice = new Customer("3", "alice@example.com", "Alice",
      new Address("Long Street", "Wroclaw"));


    customerEvents.publish(johanna, "1"); // <3>
    customerEvents.publish(bob, "2");
    customerEvents.publish(alice, "3");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {

          CustomerList customersResponse =
            await(
              componentClient.forView()
                .method(CustomersByCity::getCustomers) // <4>
                .invokeAsync(CustomersByCity.QueryParameters.of("Porto", "London"))
            );

          assertThat(customersResponse.customers()).containsOnly(johanna, bob);
        }
      );
  }
}
// end::view-test[]
