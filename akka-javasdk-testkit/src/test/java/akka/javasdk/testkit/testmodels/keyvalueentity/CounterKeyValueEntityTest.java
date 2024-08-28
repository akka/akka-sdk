/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.testmodels.keyvalueentity;

import akka.javasdk.Metadata;
import akka.javasdk.testkit.KeyValueEntityResult;
import akka.javasdk.testkit.KeyValueEntityTestKit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterKeyValueEntityTest {

  @Test
  public void testIncrease() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.call(entity -> entity.increaseBy(10));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
  }

  @Test
  public void testIncreaseWithMetadata() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.call(entity -> entity.increaseFromMeta(), Metadata.EMPTY.add("value", "10"));
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Ok");
    assertEquals(testKit.getState(), 10);
  }

  @Test
  public void testIncreaseWithNegativeValue() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    KeyValueEntityResult<String> result = testKit.call(entity -> entity.increaseBy(-10));
    assertTrue(result.isError());
    assertEquals(result.getError(), "Can't increase with a negative value");
  }

  @Test
  public void testDeleteValueEntity() {
    KeyValueEntityTestKit<Integer, CounterValueEntity> testKit =
        KeyValueEntityTestKit.of(ctx -> new CounterValueEntity());
    testKit.call(entity -> entity.increaseBy(10));
    KeyValueEntityResult<String> result = testKit.call(entity -> entity.delete());
    assertTrue(result.isReply());
    assertEquals(result.getReply(), "Deleted");
    assertEquals(testKit.getState(), 0);
  }
}
