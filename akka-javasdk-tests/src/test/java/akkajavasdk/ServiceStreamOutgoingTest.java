/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.EventingTestKit.OutgoingMessages;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.streamproducer.SequenceEntity;
import akkajavasdk.components.streamproducer.SequenceEvents;
import akkajavasdk.components.streamproducer.SequenceEvents.PublicNumber;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

// The order test runs first, while the producer still drops pub-sub events. The producer starts to
// accept them during that test, and that switch reorders events.
@ExtendWith(Junit5LogCapturing.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServiceStreamOutgoingTest extends TestKitSupport {

  private static final String SERVICE = "sdk-tests";

  @Override
  protected TestKit.Settings testKitSettings() {
    return super.testKitSettings().withStreamOutgoingMessages(SERVICE, SequenceEvents.STREAM_ID);
  }

  private OutgoingMessages outgoing() {
    OutgoingMessages outgoing =
        testKit.getStreamOutgoingMessages(SERVICE, SequenceEvents.STREAM_ID);
    outgoing.clear();
    return outgoing;
  }

  private void add(String id, int number) {
    componentClient.forEventSourcedEntity(id).method(SequenceEntity::add).invoke(number);
  }

  @Test
  @Order(2)
  public void shouldDeliverEachEventOnce() throws InterruptedException {
    OutgoingMessages outgoing = outgoing();
    String id = UUID.randomUUID().toString();

    add(id, 1);
    assertThat(outgoing.expectOneTyped(PublicNumber.class, ofSeconds(20)).getPayload())
        .isEqualTo(new PublicNumber(id, 1));

    // In test mode the producer polls the database every 500 ms. After two idle polls it emits a
    // heartbeat, and from then on it sends each event twice: from pub-sub and from the database.
    Thread.sleep(2000);

    for (int number = 2; number <= 5; number++) {
      add(id, number);
      assertThat(outgoing.expectOneTyped(PublicNumber.class, ofSeconds(20)).getPayload())
          .isEqualTo(new PublicNumber(id, number));
    }

    outgoing.expectNone(ofSeconds(2));
  }

  @Test
  @Order(1)
  public void shouldDeliverEventsInOrder() throws InterruptedException {
    OutgoingMessages outgoing = outgoing();
    String id = UUID.randomUUID().toString();

    for (int number = 1; number <= 150; number++) {
      add(id, number);
      Thread.sleep(80);
    }

    List<Integer> received = new ArrayList<>();
    for (int i = 0; i < 150; i++) {
      PublicNumber message =
          outgoing.expectOneTyped(PublicNumber.class, ofSeconds(20)).getPayload();
      assertThat(message.entityId()).isEqualTo(id);
      received.add(message.number());
    }
    assertThat(received).containsExactlyElementsOf(IntStream.rangeClosed(1, 150).boxed().toList());

    outgoing.expectNone(ofSeconds(2));
  }
}
