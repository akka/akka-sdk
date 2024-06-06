/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.pubsub;

import com.example.wiring.eventsourcedentities.counter.CounterEvent.ValueIncreased;
import com.example.wiring.valueentities.user.User;
import com.example.wiring.valueentities.user.UserSideEffect;
import kalix.javasdk.testkit.EventingTestKit.IncomingMessages;
import kalix.javasdk.testkit.EventingTestKit.Message;
import kalix.javasdk.testkit.KalixTestKit;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;

import java.util.concurrent.TimeUnit;

import static com.example.wiring.pubsub.PublishESToTopic.COUNTER_EVENTS_TOPIC;
import static java.time.Duration.ofMillis;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/*@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EventingTestkitIntegrationTest extends KalixIntegrationTestKitSupport {

  private IncomingMessages topicSubscription;

  @Override
  protected KalixTestKit.Settings kalixTestKitSettings() {
    return KalixTestKit.Settings.DEFAULT
        .withAclEnabled()
        .withAdvancedViews()
        .withWorkflowTickInterval(ofMillis(500))
        .withTopicIncomingMessages(COUNTER_EVENTS_TOPIC)
        .withValueEntityIncomingMessages("user");
  }

  @BeforeAll
  public void beforeAll() {
      super.beforeAll();
      topicSubscription = kalixTestKit.getTopicIncomingMessages(COUNTER_EVENTS_TOPIC);
  }

  @BeforeEach
  public void beforeEach() {
    DummyCounterEventStore.clear();
  }

  @Test
  public void shouldPublishEventWithTypeNameViaSubscriptionEventingTestkit() {
    //given
    String subject = "test-2";
    ValueIncreased event1 = new ValueIncreased(1);
    ValueIncreased event2 = new ValueIncreased(2);

    //when
    Message<ValueIncreased> test = kalixTestKit.getMessageBuilder().of(event1, subject);
    topicSubscription.publish(test);
    topicSubscription.publish(event2, subject);

    //then
      Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          var response = DummyCounterEventStore.get(subject);
          assertThat(response).containsOnly(event1, event2);

          var viewResponse = webClient
              .get()
              .uri("/counter-view-topic-sub/less-then/" + 4)
              .retrieve()
              .bodyToFlux(CounterView.class)
              .toStream()
              .toList();

          assertThat(viewResponse).contains(new CounterView(subject, 3));
        });
  }

  @Test
  public void shouldPublishVEDeleteMessage() {
    //given
    IncomingMessages incomingMessages = kalixTestKit.getValueEntityIncomingMessages("user");
    String subject = "123";
    User user = new User("email", "name");

    //when
    incomingMessages.publish(user, subject);

    //then
      Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          User consumedUser = UserSideEffect.getUsers().get(subject);
          assertThat(consumedUser).isEqualTo(user);
        });

    //when
    incomingMessages.publishDelete(subject);

    //then
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.of(SECONDS))
        .untilAsserted(() -> {
          User consumedUser = UserSideEffect.getUsers().get(subject);
          assertThat(consumedUser).isNull();
        });
  }
}
*/
