/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.keyvalueentity;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.EventSourcedTestKit;
import akka.javasdk.testkit.KeyValueEntityResult;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import akka.javasdk.testkit.eventsourced.PolyState;
import akka.javasdk.testkit.eventsourced.PolyStateESE;
import akka.javasdk.testkit.eventsourced.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterKeyValueEntityTest {

  @Test
  public void testIncrease() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.method(CounterValueEntity::increaseBy).invoke(10);
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
  }

  @Test
  public void testIncreaseWithMetadata() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.method(CounterValueEntity::increaseFromMeta).withMetadata(Metadata.EMPTY.add("value", "10")).invoke();
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.method(CounterValueEntity::increaseBy).invoke(-10);
    assertTrue(result.isError());
    assertFalse(testKit.isDeleted());
    assertEquals(result.getError(), "Can't increase with a negative value");
  }

  @Test
  public void testDeleteValueEntity() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    testKit.method(CounterValueEntity::increaseBy).invoke(10);
    KeyValueEntityResult<String> result = testKit.method(CounterValueEntity::delete).invoke();
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Deleted");
    assertEquals(testKit.getState(), 0);
    assertTrue(testKit.isDeleted());
    assertTrue(result.stateWasDeleted());
  }

  @Test
  public void failResponseSerDes() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
      KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    var ex = assertThrows(Exception.class, () -> {
      testKit.method(CounterValueEntity::polyResponse).invoke();
    });
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.Response$Error. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.keyvalueentity.CounterValueEntity");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }

  @Test
  public void failInputSerDes() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
      KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    var ex = assertThrows(Exception.class, () -> {
      testKit.method(CounterValueEntity::polyHandler).invoke(new Response.OK());
    });
    assertThat(ex.getMessage()).isEqualTo("Failed to serialize or deserialize akka.javasdk.testkit.eventsourced.Response$OK. Make sure that all events, commands, responses and state are serializable for akka.javasdk.testkit.keyvalueentity.CounterValueEntity");
    assertThat(ex.getCause().getMessage()).contains("could not be decoded into");
  }
}
