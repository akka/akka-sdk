package com.example.fibonacci;



import java.util.function.Predicate;
// tag::implementing-action[]
import kalix.javasdk.action.Action;
import kalix.javasdk.annotations.ActionId;

// end::implementing-action[]

import static io.grpc.Status.Code.INVALID_ARGUMENT;


// tag::implementing-action[]
@ActionId("fibonacci")
public class FibonacciAction extends Action {

  private boolean isFibonacci(long num) {  // <1>
    Predicate<Long> isPerfectSquare = (n) -> {
      long square = (long) Math.sqrt(n);
      return square * square == n;
    };
    return isPerfectSquare.test(5*num*num + 4) || isPerfectSquare.test(5*num*num - 4);
  }
  private long nextFib(long num) {   // <2>
    double result = num * (1 + Math.sqrt(5)) / 2.0;
    return Math.round(result);
  }

  public Effect<Number> getNumber(Long number) { // <3>
    return nextNumber(new Number(number));
  }

  public Effect<Number> nextNumber(Number number) {
    long num =  number.value();
    if (isFibonacci(num)) {                                     // <4>
      return effects().reply(new Number(nextFib(num)));
    } else {
      return effects()                                          // <5>
        .error("Input number is not a Fibonacci number, received '" + num + "'", INVALID_ARGUMENT);
    }
  }
}
// end::implementing-action[]
