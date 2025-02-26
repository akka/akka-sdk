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
      await(
        componentClient
          .forKeyValueEntity("foo")
          .method(CounterEntity::increaseBy)
          .invokeAsync(10)
      );

    Assertions.assertEquals(10, counterIncrease.value());
  }

  // tag::sample-it[]
  @Test
  public void verifyCounterSetAndIncrease() {

    Counter counterGet =
      await(
        componentClient // <2>
          .forKeyValueEntity("bar")
          .method(CounterEntity::get) // <3>
          .invokeAsync()
      );
    Assertions.assertEquals(0, counterGet.value());

    Counter counterPlusOne =
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::plusOne) // <4>
          .invokeAsync()
      );
    Assertions.assertEquals(1, counterPlusOne.value());

    Counter counterGetAfter = // <5>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get)
          .invokeAsync()
      );
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
// end::sample-it[]
