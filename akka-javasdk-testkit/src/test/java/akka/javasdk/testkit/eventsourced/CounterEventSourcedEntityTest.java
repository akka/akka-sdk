/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

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
    assertFalse(testKit.isDeleted());
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
  public void testDelete() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.delete());
    assertTrue(result.isReply());
    assertEquals("Ok", result.getReply());
    assertTrue(testKit.isDeleted());
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseBy(-10));
    assertTrue(result.isError());
    assertEquals("Can't increase with a negative value", result.getError());
  }

  @Test
  public void testOverflowingIncrease() {
    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.ofEntityWithState(ctx -> new CounterEventSourcedEntity(), Integer.MAX_VALUE - 10);
    EventSourcedResult<String> result = testKit.call(entity -> entity.increaseBy(11));
    assertTrue(result.isError());
    assertEquals("Can't increase by [11] due to overflow", result.getError());
  }

  @Test
  public void testOverflowingDoubleIncrease() {
    List<Increased> previousEvents = new ArrayList<>();
    int i = 1;
    while (i > 0) {
      previousEvents.add(new Increased(EventSourcedTestKit.DEFAULT_TEST_ENTITY_ID, i));
      i *= 2;
    }

    EventSourcedTestKit<Integer, Increased, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.ofEntityFromEvents(ctx -> new CounterEventSourcedEntity(), previousEvents);

    EventSourcedResult<String> result = testKit.call(entity -> entity.doubleIncreaseBy(10));
    assertTrue(result.isError());
    assertEquals("Can't double-increase by [10] due to overflow", result.getError());
  }
}
