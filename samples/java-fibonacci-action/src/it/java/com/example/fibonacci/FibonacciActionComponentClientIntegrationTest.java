package com.example.fibonacci;

import akka.platform.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// tag::testing-action[]
public class FibonacciActionComponentClientIntegrationTest extends KalixIntegrationTestKitSupport {


  @Test
  public void calculateNextNumber() throws ExecutionException, InterruptedException, TimeoutException {

    Number response =
      await(
        componentClient.forAction()
          .method(FibonacciAction::nextNumber)
          .invokeAsync(new Number(5)));

    Assertions.assertEquals(8, response.value());
  }
  // end::testing-action[]

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
