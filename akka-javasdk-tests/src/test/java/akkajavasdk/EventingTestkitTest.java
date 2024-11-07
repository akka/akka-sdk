/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import akka.javasdk.testkit.EventingTestKit;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.eventsourcedentities.counter.CounterEvent;
import akkajavasdk.components.keyvalueentities.user.User;
import akkajavasdk.components.keyvalueentities.user.UserSideEffect;
import akkajavasdk.components.pubsub.CounterView;
import akkajavasdk.components.pubsub.DummyCounterEventStore;
import akkajavasdk.components.pubsub.ViewFromCounterEventsTopic;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(Junit5LogCapturing.class)
public class EventingTestkitTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAclEnabled()
        .withTopicIncomingMessages(ViewFromCounterEventsTopic.COUNTER_EVENTS_TOPIC)
        .withKeyValueEntityIncomingMessages("user");
  }

  @BeforeEach
  public void beforeEach() {
    DummyCounterEventStore.clear();
  }

  @Test
  public void shouldPublishEventWithTypeNameViaSubscriptionEventingTestkit() {
    var topicSubscription = testKit.getTopicIncomingMessages(ViewFromCounterEventsTopic.COUNTER_EVENTS_TOPIC);

    //given
    var subject = "test-2";
    var event1 = new CounterEvent.ValueIncreased(1);
    var event2 = new CounterEvent.ValueIncreased(2);

    //when
    EventingTestKit.Message<CounterEvent.ValueIncreased> test = testKit.getMessageBuilder().of(event1, subject);
    topicSubscription.publish(test);
    topicSubscription.publish(event2, subject);

    //then
    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.of(SECONDS))
      .untilAsserted(() -> {
        var response = DummyCounterEventStore.get(subject);
        assertThat(response).containsOnly(event1, event2);

        var viewResponse = await(componentClient
            .forView()
            .method(ViewFromCounterEventsTopic::getCountersLessThan)
            .invokeAsync(new ViewFromCounterEventsTopic.QueryParameters(4)));

        assertThat(viewResponse.counters()).contains(new CounterView(subject, 3));
      });
  }

  @Test
  public void shouldPublishKVEDeleteMessage() {
    //given
    EventingTestKit.IncomingMessages incomingMessages = testKit.getKeyValueEntityIncomingMessages("user");
    String subject = "123";
    var user = new User("email", "name");

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
