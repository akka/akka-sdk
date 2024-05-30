package com.example;

import com.google.protobuf.any.Any;
import kalix.javasdk.DeferredCall;
import kalix.javasdk.client.ComponentClient;
import static kalix.javasdk.testkit.DeferredCallSupport.execute;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;


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
  @Autowired
  private WebClient webClient; // <2>

  // end::sample-it[]
  @Test
  public void verifyCounterIncrease() {

    var counterIncrease =
      execute(
        componentClient
          .forValueEntity("foo")
          .call(CounterEntity::increaseBy)
          .params(new Number(10))
      );

    Assertions.assertEquals(10, counterIncrease.value());
  }

  // tag::sample-it[]
  @Test
  public void verifyCounterSetAndIncrease() {

    Number counterGet = // <3>
      execute(
        componentClient
          .forValueEntity("bar")
          .call(CounterEntity::get)
      );
    Assertions.assertEquals(0, counterGet.value());

    Number counterPlusOne = // <4>
      execute(
        componentClient
          .forValueEntity("bar")
          .call(CounterEntity::plusOne)
      );
    Assertions.assertEquals(1, counterPlusOne.value());

    Number counterGetAfter = // <5>
      execute(
        componentClient
          .forValueEntity("bar")
          .call(CounterEntity::get)
      );
    Assertions.assertEquals(1, counterGetAfter.value());
  }

}
// end::sample-it[]
