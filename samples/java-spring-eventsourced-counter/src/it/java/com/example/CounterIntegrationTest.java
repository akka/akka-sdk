package com.example;

import com.example.actions.CounterCommandFromTopicAction;
import kalix.javasdk.CloudEvent;
import kalix.javasdk.testkit.EventingTestKit;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ActiveProfiles("with-mocked-eventing")
// tag::class[]
@SpringBootTest(classes = Main.class)
@Import(TestKitConfiguration.class)
public class CounterIntegrationTest extends KalixIntegrationTestKitSupport { // <1>

// end::class[]

  // tag::test-topic[]
  @Autowired
  private KalixTestKit kalixTestKit; // <2>
  private EventingTestKit.IncomingMessages commandsTopic;
  private EventingTestKit.OutgoingMessages eventsTopic;
  // end::test-topic[]

  private EventingTestKit.OutgoingMessages eventsTopicWithMeta;

  // tag::test-topic[]

  @BeforeAll
  public void beforeAll() {
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
      .methodRef(Counter::increase)
      .invokeAsync(10);

    var getCounterState =
      counterClient
        .methodRef(Counter::get);
    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      // check state until returns 10
      .until(
        () -> getCounterState.invokeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS),
        new IsEqual("\"10\""));

    // multiply by 20 (from 10 to 200
    counterClient
      .methodRef(Counter::multiply)
      .invokeAsync(20);

    Awaitility.await()
      .ignoreExceptions()
      .atMost(20, TimeUnit.SECONDS)
      // check state until returns 200
      .until(
        () -> getCounterState.invokeAsync().toCompletableFuture().get(1, TimeUnit.SECONDS),
        new IsEqual("\"200\""));
  }

  // tag::test-topic[]

  @Test
  public void verifyCounterEventSourcedPublishToTopic()  {
    var counterId = "test-topic";
    var increaseCmd = new CounterCommandFromTopicAction.IncreaseCounter(counterId, 3);
    var multipleCmd = new CounterCommandFromTopicAction.MultiplyCounter(counterId, 4);

    commandsTopic.publish(increaseCmd, counterId); // <4>
    commandsTopic.publish(multipleCmd, counterId);

    var eventIncreased = eventsTopic.expectOneTyped(CounterEvent.ValueIncreased.class); // <5>
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
