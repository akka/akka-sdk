package com.example;

import akka.javasdk.testkit.TestKitSupport;
import com.example.application.CounterEntity;
import com.example.domain.Counter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// tag::sample-it[]
public class CounterIntegrationTest extends TestKitSupport { // <1>

  // end::sample-it[]
  @Test
  public void verifyCounterIncrease() {
    var counterIncrease =
      componentClient
        .forKeyValueEntity("foo")
        .method(CounterEntity::increaseBy)
        .invoke(10);

    Assertions.assertEquals(10, counterIncrease.value());
  }

  // tag::sample-it[]
  @Test
  public void verifyCounterSetAndIncrease() {

    Counter counterGet =
        componentClient // <2>
          .forKeyValueEntity("bar")
          .method(CounterEntity::get) // <3>
          .invoke();
    Assertions.assertEquals(0, counterGet.value());

    Counter counterPlusOne =
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::plusOne) // <4>
          .invoke();
    Assertions.assertEquals(1, counterPlusOne.value());

    Counter counterGetAfter = // <5>
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get)
          .invoke();
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
// end::sample-it[]
