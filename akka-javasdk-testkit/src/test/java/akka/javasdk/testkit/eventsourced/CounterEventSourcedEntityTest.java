/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eventsourced;

import akka.javasdk.Metadata;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.testkit.EventSourcedResult;
import akka.javasdk.testkit.EventSourcedTestKit;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import akka.javasdk.testkit.keyvalueentity.CounterValueEntity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterEventSourcedEntityTest {

  private static EventSourcedEntity.Effect<String> apply(CounterEventSourcedEntity entity) {
    return entity.set(11);
  }

  @Test
  public void testIncrease() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::increaseBy).invoke(10);
    assertTrue(result.isReply());
    assertEquals("Ok", result.getReply());
    assertEquals(10, testKit.getState());
    assertEquals(1, testKit.getAllEvents().size());
    assertFalse(testKit.isDeleted());
  }

  @Test
  public void testIncreaseWithMetadata() {
    String counterId = "123";
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(counterId, ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::increaseFromMeta)
      .withMetadata(Metadata.EMPTY.add("value", "10"))
      .invoke();
    assertTrue(result.isReply());
    assertEquals(new CounterEvent.Increased(counterId, 10), result.getNextEventOfType(CounterEvent.class));
    assertEquals("Ok", result.getReply());
    assertEquals(10, testKit.getState());
    assertEquals(1, testKit.getAllEvents().size());
  }

  @Test
  public void testDoubleIncrease() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::doubleIncreaseBy).invoke(10);
    assertTrue(result.isReply());
    assertEquals("Ok", result.getReply());
    assertEquals(20, testKit.getState());
    assertEquals(2, testKit.getAllEvents().size());
  }

  @Test
  public void testDelete() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::delete).invoke();
    assertTrue(result.isReply());
    assertEquals("Ok", result.getReply());
    assertTrue(testKit.isDeleted());
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::increaseBy).invoke(-10);
    assertTrue(result.isError());
    assertEquals("Can't increase with a negative value", result.getError());
  }

  @Test
  public void testOverflowingIncrease() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.ofEntityWithState(ctx -> new CounterEventSourcedEntity(), Integer.MAX_VALUE - 10);
    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::increaseBy).invoke(11);
    assertTrue(result.isError());
    assertEquals("Can't increase by [11] due to overflow", result.getError());
  }

  @Test
  public void testOverflowingDoubleIncrease() {
    List<CounterEvent> previousEvents = new ArrayList<>();
    int i = 1;
    while (i > 0) {
      previousEvents.add(new CounterEvent.Increased(EventSourcedTestKit.DEFAULT_TEST_ENTITY_ID, i));
      i *= 2;
    }

    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.ofEntityFromEvents(ctx -> new CounterEventSourcedEntity(), previousEvents);

    EventSourcedResult<String> result = testKit.method(CounterEventSourcedEntity::doubleIncreaseBy).invoke(10);
    assertTrue(result.isError());
    assertEquals("Can't double-increase by [10] due to overflow", result.getError());
  }

  @Test
  public void testCollectionReturnType() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
        EventSourcedTestKit.of(ctx -> new CounterEventSourcedEntity());
    var result = testKit.method(CounterEventSourcedEntity::returnList).invoke();
    assertThat(result.getReply()).asList().hasSize(1);
    assertThat(result.getReply().getFirst()).isEqualTo(new CounterEventSourcedEntity.SomeRecord("ok"));
  }

  @Test
  public void failEventSerDes() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.ofEntityWithState(ctx -> new CounterEventSourcedEntity(), 0);

    var ex = assertThrows(Exception.class, () -> testKit.method(CounterEventSourcedEntity::set).invoke(10));
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.CounterEvent$Set. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.eventsourced.CounterEventSourcedEntity");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }

  @Test
  public void failResponseSerDes() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.ofEntityWithState(ctx -> new CounterEventSourcedEntity(), 0);

    var ex = assertThrows(Exception.class, () -> testKit.method(CounterEventSourcedEntity::commandHandlerWithResponse).invoke());
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.Response$Error. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.eventsourced.CounterEventSourcedEntity");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }

  @Test
  public void failStateSerDes() {
    var ex = assertThrows(Exception.class, () -> {
        EventSourcedTestKit.ofEntityWithState(ctx -> new PolyStateESE(), new PolyState.StateA());
    });
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.PolyState$StateA. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.eventsourced.PolyStateESE");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }

  @Test
  public void failInputSerDes() {
    EventSourcedTestKit<Integer, CounterEvent, CounterEventSourcedEntity> testKit =
      EventSourcedTestKit.ofEntityWithState(ctx -> new CounterEventSourcedEntity(), 0);

    var ex = assertThrows(Exception.class, () -> testKit.method(CounterEventSourcedEntity::commandHandlerWithPolyInput).invoke(new Response.OK()));
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.Response$OK. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.eventsourced.CounterEventSourcedEntity");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }
}
