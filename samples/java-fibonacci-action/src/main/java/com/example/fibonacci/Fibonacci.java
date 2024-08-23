package com.example.fibonacci;

import java.util.function.Predicate;

public class Fibonacci {

  public static boolean isFibonacci(long num) {
    Predicate<Long> isPerfectSquare = (n) -> {
      long square = (long) Math.sqrt(n);
      return square * square == n;
    };
    return isPerfectSquare.test(5 * num * num + 4) || isPerfectSquare.test(5 * num * num - 4);
  }

  public static Number nextFib(long num) {
    if (isFibonacci(num)) {
      double result = num * (1 + Math.sqrt(5)) / 2.0;
      return new Number(Math.round(result));
    } else {
      throw new IllegalArgumentException("Input number is not a Fibonacci number, received '" + num + "'");
    }
  }
}
