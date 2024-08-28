package customer.api;

import customer.views.Customer;
import customer.views.CustomerPublicEvent.Created;
import customer.views.CustomersByEmailView;
import customer.views.CustomersByNameView;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.KalixTestKit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomersByNameViewIntegrationTest extends CustomerRegistryIntegrationTest {

  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
      return KalixTestKit.Settings.DEFAULT.withAclEnabled()
              .withStreamIncomingMessages("customer-registry", "customer_events");
  }

  @Test
  public void shouldReturnCustomersFromViews() {
    IncomingMessages customerEvents = kalixTestKit.getStreamIncomingMessages("customer-registry", "customer_events");

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
              .invokeAsync(new CustomersByNameView.QueryParameters(created1.name()))
          ).customers().stream().findFirst().get();

        assertThat(customer).isEqualTo(new Customer("b", created1.email(), created1.name()));

        Customer customer2 =
          await(
            componentClient.forView()
              .method(CustomersByEmailView::findByEmail)
              .invokeAsync(new CustomersByEmailView.QueryParameters(created2.email()))
          ).customers().stream().findFirst().get();

        assertThat(customer2).isEqualTo(new Customer("a", created2.email(), created2.name()));

        }
      );
  }
}
