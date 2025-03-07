package counter.application;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventingTestKit.IncomingMessages;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import counter.application.CounterStore.CounterEntry;
import counter.domain.CounterEvent;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class CounterStoreUpdaterTest extends TestKitSupport {

  private IncomingMessages counterEvents;
  private CounterStore counterStore;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withEventSourcedEntityIncomingMessages("counter");
  }


  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    counterEvents = testKit.getEventSourcedEntityIncomingMessages("counter");
    counterStore = getDependency(CounterStore.class);
  }


  @Test
  public void verifyBuildInDeduplication() {
    var messageBuilder = testKit.getMessageBuilder();

    var event1 = new CounterEvent.ValueIncreased(1, 1);
    var event2 = new CounterEvent.ValueIncreased(5, 6);

    // preparing metadata with sequence numbers
    Metadata event1Metadata = messageBuilder.defaultMetadata(event1, "c123").asCloudEvent()
      .withSequence("1").asMetadata();
    Metadata event2Metadata = messageBuilder.defaultMetadata(event2, "c123").asCloudEvent()
      .withSequence("2").asMetadata();

    // sending predefined events
    counterEvents.publish(messageBuilder.of(event1, event1Metadata));
    counterEvents.publish(messageBuilder.of(event2, event2Metadata));

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, SECONDS)
      .untilAsserted(() -> {
        Collection<CounterEntry> result = await(counterStore.getAll());
        assertThat(result).containsOnly(new CounterEntry("c123", 6, 2));
      });

    // sending the same events again
    counterEvents.publish(messageBuilder.of(event1, event1Metadata));
    counterEvents.publish(messageBuilder.of(event2, event2Metadata));

    Awaitility.await()
      .ignoreExceptions()
      .during(3, SECONDS)
      .untilAsserted(() -> {
        Collection<CounterEntry> result = await(counterStore.getAll());
        assertThat(result).containsOnly(new CounterEntry("c123", 6, 2));
      });


  }
}