package com.example.domain;

public record Counter(int value) {
  public Counter increment(int delta) {
    return new Counter(value + delta);
  }
}