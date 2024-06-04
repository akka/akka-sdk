package com.example.fibonacci;

import com.example.Main;
import kalix.spring.testkit.KalixIntegrationTestKitSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;



@DirtiesContext
// tag::testing-action[]
@SpringBootTest(classes = Main.class)
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
  public void calculateNextNumberWithLimitedFibo() throws ExecutionException, InterruptedException, TimeoutException {

    Number response =
      await(
        componentClient.forAction()
          .method(LimitedFibonacciAction::nextNumber)
          .invokeAsync(new Number(5)));

    Assertions.assertEquals(8, response.value());
  }

  // tag::testing-action[]
}
// end::testing-action[]
