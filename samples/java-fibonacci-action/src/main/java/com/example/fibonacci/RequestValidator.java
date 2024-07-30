package com.example.fibonacci;

public class RequestValidator {
  public boolean isValid(Long number) {
    return number > 0 & number < 10000;
  }
}
