package customer.application;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEvent.CustomerCreated;
import customer.domain.CustomerEvent.NameChanged;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class CustomerStoreUpdaterTest extends TestKitSupport {

  private IncomingMessages consumerEvents;
  private CustomerStore customerStore;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withEventSourcedEntityIncomingMessages("customer");
  }


  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    consumerEvents = testKit.getEventSourcedEntityIncomingMessages("customer");
    customerStore = getDependency(CustomerStore.class);
  }


  @Test
  public void verifyIdempotencyOfCustomerStoreUpdater() {
    var messageBuilder = testKit.getMessageBuilder();

    var event1 = new CustomerCreated("email1", "name1", new Address("street1", "city1"));
    var event2 = new NameChanged("name2");

    // preparing metadata with sequence numbers
    Metadata event1Metadata = messageBuilder.defaultMetadata(event1, "c123").asCloudEvent()
      .withSequence("1").asMetadata();
    Metadata event2Metadata = messageBuilder.defaultMetadata(event2, "c123").asCloudEvent()
      .withSequence("2").asMetadata();

    // sending predefined events
    consumerEvents.publish(messageBuilder.of(event1, event1Metadata));
    consumerEvents.publish(messageBuilder.of(event2, event2Metadata));

    Customer expectedCustomer = new Customer("email1", "name2", new Address("street1", "city1"));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, SECONDS)
      .untilAsserted(() -> {
        var result = await(customerStore.getAll());
        assertThat(result).containsOnly(expectedCustomer);
      });

    // sending the same events again
    consumerEvents.publish(messageBuilder.of(event1, event1Metadata));
    consumerEvents.publish(messageBuilder.of(event2, event2Metadata));

    Awaitility.await()
      .ignoreExceptions()
      .during(3, SECONDS)
      .untilAsserted(() -> {
        var result = await(customerStore.getAll());
        assertThat(result).containsOnly(expectedCustomer);
      });


  }
}