package customer.api;

import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.TestKit;
import customer.application.CustomerPublicEvent.Created;
import customer.application.CustomersByEmailView;
import customer.application.CustomersByNameView;
import customer.domain.CustomerEntry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomersByNameViewIntegrationTest extends CustomerRegistryIntegrationTest {

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

          CustomerEntry customer = componentClient.forView()
            .method(CustomersByNameView::findByName)
            .invoke(created1.name())
            .customers().stream().findFirst().get();

          assertThat(customer).isEqualTo(new CustomerEntry("b", created1.email(), created1.name()));

          CustomerEntry customer2 = componentClient.forView()
            .method(CustomersByEmailView::findByEmail)
            .invoke(created2.email())
            .customers().stream().findFirst().get();

          assertThat(customer2).isEqualTo(new CustomerEntry("a", created2.email(), created2.name()));

        }
      );
  }
}
