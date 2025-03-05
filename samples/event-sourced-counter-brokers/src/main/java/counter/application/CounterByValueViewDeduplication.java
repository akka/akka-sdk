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

@ComponentId("counter-by-value-deduplication")
public class CounterByValueViewDeduplication extends View {

  // tag::seq-tracking[]
  public record CounterByValue(String name, int value, long currentSeqNum) { // <1>
  }

  // end::seq-tracking[]

  public record CounterByValueList(List<CounterByValue> counters) {
  }

  // tag::seq-tracking[]
  @Consume.FromEventSourcedEntity(CounterEntity.class)
  public static class CounterByValueUpdater extends TableUpdater<CounterByValue> {

    public Effect<CounterByValue> onEvent(CounterEvent counterEvent) {
      var newSeqNum = updateContext().metadata().asCloudEvent().sequence();
      var currentSeqNum = Optional.ofNullable(rowState()).map(CounterByValue::currentSeqNum).orElse(0L);
      if (!newSeqNum.isPresent()) { // <2>
        // missing sequence number, can't deduplicate
        return processEvent(counterEvent, 0L);
      } else {
        if (newSeqNum.get() <= currentSeqNum) {
          //duplicate, can be ignored
          return effects().ignore(); // <3>
        } else {
          // not a duplicate
          return processEvent(counterEvent, newSeqNum.get()); // <4>
        }
      }
    }

    private Effect<CounterByValue> processEvent(CounterEvent counterEvent, Long seqNum) {
      var name = updateContext().eventSubject().get();
      var currentValue = Optional.ofNullable(rowState()).map(CounterByValue::value).orElse(0);
      return switch (counterEvent) {
        case ValueIncreased increased -> effects().updateRow(
          new CounterByValue(name, currentValue + increased.value(), seqNum)); // <5>
        case ValueMultiplied multiplied -> effects().updateRow(
          new CounterByValue(name, currentValue * multiplied.multiplier(), seqNum)); // <5>
      };
    }
  }
  // end::seq-tracking[]

  @Query("SELECT * AS counters FROM counter_by_value WHERE value > :value")
  public QueryEffect<CounterByValueList> findByCountersByValueGreaterThan(int value) {
    return queryResult();
  }

  @Query("SELECT * AS counters FROM counter_by_value")
  public QueryEffect<CounterByValueList> findAll() {
    return queryResult();
  }
}

