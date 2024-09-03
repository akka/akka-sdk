package com.example;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * This is a skeleton for implementing integration tests for a Kalix application built with the Java SDK.
 *
 * This test will initiate a Kalix Runtime using testcontainers and therefore it's required to have Docker installed
 * on your machine. This test will also start your Spring Boot application.
 *
 * Since this is an integration tests, it interacts with the application using a WebClient
 * (already configured and provided automatically through injection).
 */

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
          .invokeAsync(new Number(10))
      );

    Assertions.assertEquals(10, counterIncrease.value());
  }

  // tag::sample-it[]
  @Test
  public void verifyCounterSetAndIncrease() {

    Number counterGet = // <3>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(0, counterGet.value());

    Number counterPlusOne = // <4>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::plusOne).invokeAsync()
      );
    Assertions.assertEquals(1, counterPlusOne.value());

    Number counterGetAfter = // <5>
      await(
        componentClient
          .forKeyValueEntity("bar")
          .method(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
// end::sample-it[]
