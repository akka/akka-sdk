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

  public record CounterByValue(String name, int value) {
  }


  public record CounterByValueList(List<CounterByValue> counters) {
  }

  // tag::events-enrichment[]
  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValue> {
    public Effect<CounterByValue> onEvent(CounterEvent counterEvent) {
      var name = updateContext().eventSubject().get();
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValue(name, increased.updatedValue())); // <1>
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValue(name, multiplied.updatedValue())); // <1>
      };
    }
  }
  // end::events-enrichment[]

  @Query("SELECT * AS counters FROM counter_by_value WHERE value > :value")
  public QueryEffect<CounterByValueList> findByCountersByValueGreaterThan(int value) {
    return queryResult();
  }

  @Query("SELECT * AS counters FROM counter_by_value")
  public QueryEffect<CounterByValueList> findAll() {
    return queryResult();
  }
  // tag::events-enrichment[]
}
// end::events-enrichment[]


