package com.example.fibonacci.api;

public class RequestValidator {
  public boolean isValid(long number) {
    return number > 0 & number < 10000;
  }
}
