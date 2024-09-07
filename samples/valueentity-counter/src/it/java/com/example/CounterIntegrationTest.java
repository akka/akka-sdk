package com.example;

import akka.javasdk.testkit.TestKitSupport;
import com.example.application.CounterEntity;
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

    Assertions.assertEquals(10, counterIncrease);
  }

  // tag::sample-it[]
  @Test
  public void verifyCounterSetAndIncrease() {

    Integer counterGet = // <3>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(0, counterGet);

    Integer counterPlusOne = // <4>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::plusOne).invokeAsync()
      );
    Assertions.assertEquals(1, counterPlusOne);

    Integer counterGetAfter = // <5>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(1, counterGetAfter);
  }

}
// end::sample-it[]
