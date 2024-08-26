package com.example.fibonacci;


import akka.platform.javasdk.timedaction.TimedAction;
import akka.platform.javasdk.annotations.ComponentId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// tag::implementing-action[]
@ComponentId("fibonacci")
public class FibonacciTimedAction extends TimedAction {

  private final Logger logger = LoggerFactory.getLogger(FibonacciTimedAction.class);

  public Effect calculateNextNumber(Long number) { // <3>
    try {
      logger.info("Request for the next number {}", Fibonacci.nextFib(number));
      return effects().done();
    } catch (IllegalArgumentException e) {
      return effects().error(e.getMessage());
    }
  }

}
// end::implementing-action[]
