package com.example;

import kalix.javasdk.annotations.TypeName;

public sealed interface CounterEvent {

  @TypeName("value-increased")
  record ValueIncreased(int value) implements CounterEvent {
  }

  @TypeName("value-multiplied")
  record ValueMultiplied(int value) implements CounterEvent {
  }
}
