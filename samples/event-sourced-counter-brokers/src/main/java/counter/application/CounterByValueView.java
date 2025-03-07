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
import java.util.Optional;

// tag::not-idempotent-update[]
@ComponentId("counter-by-value")
public class CounterByValueView extends View {

  public record CounterByValue(String name, int value) {
  }

  // end::not-idempotent-update[]

  public record CounterByValueList(List<CounterByValue> counters) {
  }

  // tag::not-idempotent-update[]
  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValue> {
    public Effect<CounterByValue> onEvent(CounterEvent counterEvent) {
      var name = updateContext().eventSubject().get();
      var currentRow = rowState();
      var currentValue = Optional.ofNullable(currentRow).map(CounterByValue::value).orElse(0);
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValue(name, currentValue + increased.value())); // <1>
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValue(name, currentValue * multiplied.multiplier())); // <2>
      };
    }
  }
  // end::not-idempotent-update[]

  @Query("SELECT * AS counters FROM counter_by_value WHERE value > :value")
  public QueryEffect<CounterByValueList> findByCountersByValueGreaterThan(int value) {
    return queryResult();
  }

  @Query("SELECT * AS counters FROM counter_by_value")
  public QueryEffect<CounterByValueList> findAll() {
    return queryResult();
  }
  // tag::not-idempotent-update[]
}
// end::not-idempotent-update[]


