package com.example.fibonacci.application;


import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.annotations.ComponentId;
import com.example.fibonacci.domain.Fibonacci;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


// tag::implementing-action[]
@ComponentId("fibonacci")
public class FibonacciTimedAction extends TimedAction {

  private final Logger logger = LoggerFactory.getLogger(FibonacciTimedAction.class);

  public Effect calculateNextNumber(Long number) { // <3>
    try {
      logger.info("Request for the next number [{}].", Fibonacci.nextFib(number).value());
      return effects().done();
    } catch (IllegalArgumentException e) {
      return effects().error(e.getMessage());
    }
  }

}
// end::implementing-action[]
