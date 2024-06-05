package customer.api;

import customer.views.Customer;
import customer.views.CustomerPublicEvent.Created;
import kalix.javasdk.testkit.EventingTestKit.IncomingMessages;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomersByNameViewIntegrationTest extends KalixIntegrationTestKitSupport {

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
          Customer customer = webClient.get()
            .uri("/customers/by_name/" + bob)
            .retrieve()
            .bodyToFlux(Customer.class)
            .blockFirst(timeout);

          assertThat(customer).isEqualTo(new Customer("b", created1.email(), created1.name()));

          Customer customer2 = webClient.get()
            .uri("/customers/by_email/" + created2.email())
            .retrieve()
            .bodyToFlux(Customer.class)
            .blockFirst(timeout);

          assertThat(customer2).isEqualTo(new Customer("a", created2.email(), created2.name()));
        }
      );
  }
}
