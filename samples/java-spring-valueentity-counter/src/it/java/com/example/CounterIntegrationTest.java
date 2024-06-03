package com.example;

import kalix.javasdk.client.ComponentClient;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;




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
@SpringBootTest(classes = Main.class)
public class CounterIntegrationTest extends KalixIntegrationTestKitSupport { // <1>

  @Autowired
  private ComponentClient componentClient;

  // end::sample-it[]
  @Test
  public void verifyCounterIncrease() {

    var counterIncrease =
      await(
        componentClient
          .forValueEntity("foo")
          .methodRef(CounterEntity::increaseBy)
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
          .forValueEntity("bar")
          .methodRef(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(0, counterGet.value());

    Number counterPlusOne = // <4>
      await(
        componentClient
          .forValueEntity("bar")
          .methodRef(CounterEntity::plusOne).invokeAsync()
      );
    Assertions.assertEquals(1, counterPlusOne.value());

    Number counterGetAfter = // <5>
      await(
        componentClient
          .forValueEntity("bar")
          .methodRef(CounterEntity::get).invokeAsync()
      );
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
// end::sample-it[]
