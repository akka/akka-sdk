package com.example;

import akka.javasdk.testkit.KeyValueEntityTestKit;
import com.example.application.CounterEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CounterTest {

  @Test
  public void testIncrease() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new);
    var result = testKit.method(CounterEntity::increaseBy).invoke(10);

    assertTrue(result.isReply());
    assertEquals(10, result.getReply().value());
    assertEquals(10, testKit.getState().value());
  }

  // tag::example[]
  @Test
  public void testSetAndIncrease() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new); // <1>

    var resultSet = testKit.method(CounterEntity::set).invoke(10); // <2>
    assertTrue(resultSet.isReply());
    assertEquals(10, resultSet.getReply().value()); // <3>

    var resultPlusOne = testKit.method(CounterEntity::plusOne).invoke(); // <4>
    assertTrue(resultPlusOne.isReply());
    assertEquals(11, resultPlusOne.getReply().value());

    assertEquals(11, testKit.getState().value()); // <5>
  }
  // end::example[]

  @Test
  public void testDelete() {
    var testKit = KeyValueEntityTestKit.of(CounterEntity::new);
    testKit.method(CounterEntity::increaseBy).invoke(10);

    testKit.method(CounterEntity::delete).invoke();

    assertEquals(0, testKit.getState().value());
  }
}
