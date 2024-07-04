package com.example;

import com.example.actions.CounterCommandFromTopicAction;
import akka.platform.javasdk.CloudEvent;
import akka.platform.javasdk.testkit.EventingTestKit;
import akka.platform.javasdk.testkit.KalixTestKit;
import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// tag::class[]
public class CounterIntegrationTest extends KalixIntegrationTestKitSupport { // <1>

// end::class[]

  // tag::acls[]
  // tag::eventing-config[]
  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT
            // end::eventing-config[]
            .withAclEnabled() // <1>
            // end::acls[]
            // tag::eventing-config[]
            .withTopicIncomingMessages("counter-commands") // <1>
            .withTopicOutgoingMessages("counter-events") // <2>
            // end::eventing-config[]
            .withTopicOutgoingMessages("counter-events-with-meta");
    // tag::eventing-config[]
    // tag::acls[]
  }

  // tag::test-topic[]
  private EventingTestKit.IncomingMessages commandsTopic;
  private EventingTestKit.OutgoingMessages eventsTopic;
  // end::test-topic[]

  private EventingTestKit.OutgoingMessages eventsTopicWithMeta;

  // tag::test-topic[]

  @BeforeAll
  public void beforeAll() {
    super.beforeAll();
    // <2>
    commandsTopic = kalixTestKit.getTopicIncomingMessages("counter-commands"); // <3>
    eventsTopic = kalixTestKit.getTopicOutgoingMessages("counter-events");
    // end::test-topic[]

    eventsTopicWithMeta = kalixTestKit.getTopicOutgoingMessages("counter-events-with-meta");
    // tag::test-topic[]
  }
  // end::test-topic[]

  // since multiple tests are using the same topics, make sure to reset them before each new test
  // so unread messages from previous tests do not mess with the current one
  // tag::clear-topics[]
  @BeforeEach // <1>
  public void clearTopics() {
    eventsTopic.clear(); // <2>
    eventsTopicWithMeta.clear();
  }
  // end::clear-topics[]

  @Test
  public void verifyCounterEventSourcedWiring() {

    var counterClient = componentClient.forEventSourcedEntity("001");

    // increase counter (from 0 to 10)
    counterClient
      .method(Counter::increase)
      .invokeAsync(10);

    var getCounterState =
      counterClient
        .method(Counter::get);
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      // check state until returns 10
      .until(() -> await(getCounterState.invokeAsync()), new IsEqual<>(10));

    // multiply by 20 (from 10 to 200
    counterClient
      .method(Counter::multiply)
      .invokeAsync(20);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      // check state until returns 200
      .until(() -> await(getCounterState.invokeAsync()), new IsEqual<>(200));
  }

  // tag::test-topic[]

  @Test
  public void verifyCounterEventSourcedPublishToTopic()  {
    var counterId = "test-topic";
    var increaseCmd = new CounterCommandFromTopicAction.IncreaseCounter(counterId, 3);
    var multipleCmd = new CounterCommandFromTopicAction.MultiplyCounter(counterId, 4);

    commandsTopic.publish(increaseCmd, counterId); // <4>
    commandsTopic.publish(multipleCmd, counterId);

    var eventIncreased = eventsTopic.expectOneTyped(CounterEvent.ValueIncreased.class, Duration.ofSeconds(20)); // <5>
    var eventMultiplied = eventsTopic.expectOneTyped(CounterEvent.ValueMultiplied.class);

    assertEquals(increaseCmd.value(), eventIncreased.getPayload().value()); // <6>
    assertEquals(multipleCmd.value(), eventMultiplied.getPayload().value());
  }
  // end::test-topic[]

  // tag::test-topic-metadata[]
  @Test
  public void verifyCounterCommandsAndPublishWithMetadata() {
    var counterId = "test-topic-metadata";
    var increaseCmd = new CounterCommandFromTopicAction.IncreaseCounter(counterId, 10);

    var metadata = CloudEvent.of( // <1>
        "cmd1",
        URI.create("CounterTopicIntegrationTest"),
        increaseCmd.getClass().getName())
      .withSubject(counterId) // <2>
      .asMetadata()
      .add("Content-Type", "application/json"); // <3>

    commandsTopic.publish(kalixTestKit.getMessageBuilder().of(increaseCmd, metadata)); // <4>

    var increasedEvent = eventsTopicWithMeta.expectOneTyped(CounterCommandFromTopicAction.IncreaseCounter.class);
    var actualMd = increasedEvent.getMetadata(); // <5>
    assertEquals(counterId, actualMd.asCloudEvent().subject().get()); // <6>
    assertEquals("application/json", actualMd.get("Content-Type").get());
  }
  // end::test-topic-metadata[]
// tag::class[]
}
// end::class[]
