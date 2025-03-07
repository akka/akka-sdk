package counter.domain;

import akka.javasdk.annotations.TypeName;

// tag::events-enrichment[]
public sealed interface CounterEvent {

  // end::events-enrichment[]
  @TypeName("valie-increased")
  record ValueIncreased(int value, int currentValue) implements CounterEvent {
  }

  @TypeName("value-multiplied")
  // tag::events-enrichment[]
  record ValueMultiplied(int multiplier,
                         int currentValue) // <1>
    implements CounterEvent {
  }
}
// end::events-enrichment[]
