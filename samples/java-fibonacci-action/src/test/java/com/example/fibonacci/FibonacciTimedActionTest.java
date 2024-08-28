package com.example.fibonacci;

// tag::testing-action[]
import akka.javasdk.testkit.TimedActionResult;
import akka.javasdk.testkit.TimedActionTestkit;
import org.junit.jupiter.api.Test;

// end::testing-action[]

import static org.junit.jupiter.api.Assertions.assertTrue;

// tag::testing-action[]
public class FibonacciTimedActionTest {

  @Test
  public void testNextFib() {
    TimedActionTestkit<FibonacciTimedAction> testkit = TimedActionTestkit.of(FibonacciTimedAction::new); // <1>
    TimedActionResult result = testkit.call(a -> a.calculateNextNumber(3L));  // <2>
    assertTrue(result.isDone());
  }

  @Test
  public void testNextFibError() {
    TimedActionTestkit<FibonacciTimedAction> testkit = TimedActionTestkit.of(FibonacciTimedAction::new);  // <1>
    TimedActionResult result = testkit.call(a -> a.calculateNextNumber(4L));     // <2>
    assertTrue(result.isError());
    assertTrue(result.getError().startsWith("Input number is not a Fibonacci number"));
  }


}
// end::testing-action[]
