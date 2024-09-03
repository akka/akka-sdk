package com.example;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.api.Number;
import com.example.application.CounterEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterTest {

  @Test
  public void testIncrease() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new);
    var result = testKit.call(e -> e.increaseBy(10));

    assertTrue(result.isReply());
    assertEquals(10, result.getReply());
    assertEquals(10, testKit.getState());
  }

  // tag::example[]
  @Test
  public void testSetAndIncrease() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new); // <1>

    var resultSet = testKit.call(e -> e.set(10)); // <2>
    assertTrue(resultSet.isReply());
    assertEquals(10, resultSet.getReply()); // <3>

    var resultPlusOne = testKit.call(CounterEntity::plusOne); // <4>
    assertTrue(resultPlusOne.isReply());
    assertEquals(11, resultPlusOne.getReply());

    assertEquals(11, testKit.getState()); // <5>
  }
  // end::example[]

  @Test
  public void testDelete() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new);
    testKit.call(e -> e.increaseBy(10));

    testKit.call(e -> e.delete());

    assertEquals(0, testKit.getState());
  }
}
