/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.testmodels.eventsourced;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterEventSourcedEntityTest {

  @Test
  public void testIncrease() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseBy(10));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
    assertEquals(testKit.getAllEvents().size(), 1);
  }

  @Test
  public void testIncreaseWithMetadata() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseFromMeta(), Metadata.EMPTY.add("value", "10"));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
    assertEquals(testKit.getAllEvents().size(), 1);
  }

  @Test
  public void testDoubleIncrease() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.doubleIncreaseBy(10));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 20);
    assertEquals(testKit.getAllEvents().size(), 2);
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseBy(-10));
    assertTrue(result.isError());
    assertEquals(result.getError(), "Can't increase with a negative value");
  }
}
