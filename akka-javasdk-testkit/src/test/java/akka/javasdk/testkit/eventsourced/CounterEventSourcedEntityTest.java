/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

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
    assertEquals("Ok", result.getReply());
    assertEquals(10, testKit.getState());
    assertEquals(1, testKit.getAllEvents().size());
  }

  @Test
  public void testIncreaseWithMetadata() {
    String counterId = "123";
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(counterId, ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseFromMeta(), Metadata.EMPTY.add("value", "10"));
    assertTrue(result.isReply());
    assertEquals(new Increased(counterId, 10), result.getNextEventOfType(Increased.class));
    assertEquals("Ok", result.getReply());
    assertEquals(10, testKit.getState());
    assertEquals(1, testKit.getAllEvents().size());
  }

  @Test
  public void testDoubleIncrease() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.doubleIncreaseBy(10));
    assertTrue(result.isReply());
    assertEquals("Ok", result.getReply());
    assertEquals(20, testKit.getState());
    assertEquals(2, testKit.getAllEvents().size());
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseBy(-10));
    assertTrue(result.isError());
    assertEquals("Can't increase with a negative value", result.getError());
  }
}
