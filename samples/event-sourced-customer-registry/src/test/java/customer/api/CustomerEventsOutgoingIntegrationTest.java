package customer.api;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventingTestKit.OutgoingMessages;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import customer.application.CustomerEntity;
import customer.domain.Address;
import customer.domain.Customer;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// tag::class[]
public class CustomerEventsOutgoingIntegrationTest extends TestKitSupport {

  // tag::eventing-config[]
  @Override
  protected TestKit.Settings testKitSettings() {
    return super.testKitSettings()
      .withStreamOutgoingMessages("customer-registry", "customer_events"); // <1>
  }

  // end::eventing-config[]

  @Test
  public void shouldCaptureCreatedEvent() {
    OutgoingMessages outgoing = testKit.getStreamOutgoingMessages( // <2>
      "customer-registry",
      "customer_events"
    );

    String id = UUID.randomUUID().toString();
    componentClient // <3>
      .forEventSourcedEntity(id)
      .method(CustomerEntity::create)
      .invoke(
        new Customer("foo@example.com", "Johanna", new Address("Regent Street", "London"))
      );

    var msg = outgoing.expectOneTyped(CustomerPublicEvent.Created.class, ofSeconds(20)); // <4>
    assertThat(msg.getPayload().email()).isEqualTo("foo@example.com");
    assertThat(msg.getPayload().name()).isEqualTo("Johanna");
  }

  // end::class[]

  @Test
  public void shouldCaptureMultiplePublicEvents() {
    OutgoingMessages outgoing = testKit.getStreamOutgoingMessages(
      "customer-registry",
      "customer_events"
    );
    // drain anything produced by prior tests in the same suite
    outgoing.clear();

    String id = UUID.randomUUID().toString();
    componentClient
      .forEventSourcedEntity(id)
      .method(CustomerEntity::create)
      .invoke(
        new Customer("alice@example.com", "Alice", new Address("Baker Street", "London"))
      );
    componentClient
      .forEventSourcedEntity(id)
      .method(CustomerEntity::changeName)
      .invoke("Alicia");

    var created = outgoing.expectOneTyped(CustomerPublicEvent.Created.class, ofSeconds(20));
    assertThat(created.getPayload().name()).isEqualTo("Alice");

    var renamed = outgoing.expectOneTyped(
      CustomerPublicEvent.NameChanged.class,
      ofSeconds(20)
    );
    assertThat(renamed.getPayload().newName()).isEqualTo("Alicia");
  }

  @Test
  public void shouldNotEmitForIgnoredInternalEvents() {
    OutgoingMessages outgoing = testKit.getStreamOutgoingMessages(
      "customer-registry",
      "customer_events"
    );
    outgoing.clear();

    String id = UUID.randomUUID().toString();
    componentClient
      .forEventSourcedEntity(id)
      .method(CustomerEntity::create)
      .invoke(new Customer("bob@example.com", "Bob", new Address("Oxford Street", "London")));
    outgoing.expectOneTyped(CustomerPublicEvent.Created.class, ofSeconds(20));

    // AddressChanged is filtered by the producer (effects().ignore()) — no public event should be emitted
    componentClient
      .forEventSourcedEntity(id)
      .method(CustomerEntity::changeAddress)
      .invoke(new Address("Elm st. 5", "New Orleans"));

    outgoing.expectNone(ofSeconds(3));
  }
}
