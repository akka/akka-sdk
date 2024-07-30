package com.example.domain;

import akka.platform.javasdk.annotations.TypeName;

public sealed interface CounterEvent {

  @TypeName("value-increased")
  record ValueIncreased(int value) implements CounterEvent {
  }
}
