/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.eventsourcedentities.counter;

public record Counter(int value, String meta) {
  public Counter(int value) {
    this(value, "");
  }

  public Counter onValueIncreased(CounterEvent.ValueIncreased evt) {
    return new Counter(this.value + evt.value(), meta);
  }

  public Counter onValueSet(CounterEvent.ValueSet evt) {
    return new Counter(evt.value(), meta);
  }

  public Counter onValueMultiplied(CounterEvent.ValueMultiplied evt) {
    return new Counter(this.value * evt.value(), meta);
  }

  public Counter withMeta(String m) {
    return new Counter(value, m);
  }

  public Counter apply(CounterEvent counterEvent) {
    return switch (counterEvent) {
      case CounterEvent.ValueIncreased increased -> onValueIncreased(increased);
      case CounterEvent.ValueSet set -> onValueSet(set);
      case CounterEvent.ValueMultiplied multiplied -> onValueMultiplied(multiplied);
    };
  }
}
