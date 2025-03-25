package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;

import java.util.List;

@ComponentId("counter-by-value-enrichment")
public class CounterByValueViewEnrichment extends View {

  public record CounterByValueEntry(String name, int value) {
  }


  public record CounterByValueEntries(List<CounterByValueEntry> counters) {
  }

  // tag::events-enrichment[]
  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValueEntry> {
    public Effect<CounterByValueEntry> onEvent(CounterEvent counterEvent) {
      var name = updateContext().eventSubject().get();
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValueEntry(name, increased.updatedValue())); // <1>
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValueEntry(name, multiplied.updatedValue())); // <1>
      };
    }
  }
  // end::events-enrichment[]

  @Query("SELECT * AS counters FROM counter_by_value WHERE value > :value")
  public QueryEffect<CounterByValueEntries> findByCountersByValueGreaterThan(int value) {
    return queryResult();
  }

  @Query("SELECT * AS counters FROM counter_by_value")
  public QueryEffect<CounterByValueEntries> findAll() {
    return queryResult();
  }
  // tag::events-enrichment[]
}
// end::events-enrichment[]


