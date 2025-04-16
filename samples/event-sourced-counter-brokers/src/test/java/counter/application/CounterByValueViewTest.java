package counter.application;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import counter.application.CounterByValueView.CounterByValueList;
import counter.domain.CounterEvent.ValueIncreased;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class CounterByValueViewTest extends TestKitSupport {

  private EventingTestKit.IncomingMessages counterEvents;

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withEventSourcedEntityIncomingMessages("counter");
  }


  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    counterEvents = testKit.getEventSourcedEntityIncomingMessages("counter");
  }


  @Test
  public void verifyBuildInDeduplication() {
    var messageBuilder = testKit.getMessageBuilder();

    var event1 = new ValueIncreased(1, 1);
    var event2 = new ValueIncreased(5, 6);

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
        CounterByValueList result = componentClient.forView().method(CounterByValueView::findAll).invoke();

        assertThat(result.counters()).containsOnly(
          new CounterByValueView.CounterByValue("c123", 6)
        );
      });

    // sending the same events again
    counterEvents.publish(messageBuilder.of(event1, event1Metadata));
    counterEvents.publish(messageBuilder.of(event2, event2Metadata));

    Awaitility.await()
      .ignoreExceptions()
      .during(3, SECONDS)
      .untilAsserted(() -> {
        CounterByValueList result = componentClient.forView().method(CounterByValueView::findAll).invoke();

        //view state should be the same as before
        assertThat(result.counters()).containsOnly(
          new CounterByValueView.CounterByValue("c123", 6)
        );
      });


  }
}