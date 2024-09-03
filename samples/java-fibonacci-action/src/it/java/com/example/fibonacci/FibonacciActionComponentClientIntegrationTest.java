package com.example.fibonacci;

import akka.javasdk.testkit.TestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

// tag::testing-action[]
public class FibonacciActionComponentClientIntegrationTest extends TestKitSupport {


  @Test
  public void calculateNextNumberWithLimitedFibo() {
    Number response = await(
      httpClient.GET("/fibonacci/5/next").responseBodyAs(Number.class).invokeAsync()
    ).body();

    Assertions.assertEquals(8, response.value());
  }


  // tag::testing-action[]
}
// end::testing-action[]
