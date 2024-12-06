package customer.api;

import customer.domain.Customer;
import customer.application.CustomerPublicEvent.Created;
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.TestKit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomersByNameViewIT extends CustomerRegistryIT {

  @Override
  protected TestKit.Settings testKitSettings() {
      return super.testKitSettings()
              .withStreamIncomingMessages("customer-registry", "customer_events");
  }

  @Test
  public void shouldReturnCustomersFromViews() {
    IncomingMessages customerEvents = testKit.getStreamIncomingMessages("customer-registry", "customer_events");

    String bob = "bob";
    Created created1 = new Created("bob@gmail.com", bob);
    Created created2 = new Created("alice@gmail.com", "alice");

    customerEvents.publish(created1, "b");
    customerEvents.publish(created2, "a");

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      .pollInterval(1, TimeUnit.SECONDS)
      .untilAsserted(() -> {

        Customer customer =
          await(
            componentClient.forView()
              .method(CustomersByNameView::findByName)
              .invokeAsync(created1.name())
          ).customers().stream().findFirst().get();

        assertThat(customer).isEqualTo(new Customer("b", created1.email(), created1.name()));

        Customer customer2 =
          await(
            componentClient.forView()
              .method(CustomersByEmailView::findByEmail)
              .invokeAsync(created2.email())
          ).customers().stream().findFirst().get();

        assertThat(customer2).isEqualTo(new Customer("a", created2.email(), created2.name()));

        }
      );
  }
}
