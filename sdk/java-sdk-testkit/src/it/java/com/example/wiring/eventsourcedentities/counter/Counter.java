/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.eventsourcedentities.counter;

public record Counter(Integer value) {

  public Counter onValueIncreased(CounterEvent.ValueIncreased evt) {
    return new Counter(this.value + evt.value());
  }

  public Counter onValueSet(CounterEvent.ValueSet evt) {
    return new Counter(evt.value());
  }

  public Counter onValueMultiplied(CounterEvent.ValueMultiplied evt) {
    return new Counter(this.value * evt.value());
  }

  public Counter apply(CounterEvent counterEvent) {
    return switch (counterEvent) {
      case CounterEvent.ValueIncreased increased -> onValueIncreased(increased);
      case CounterEvent.ValueSet set -> onValueSet(set);
      case CounterEvent.ValueMultiplied multiplied -> onValueMultiplied(multiplied);
    };
  }
}
