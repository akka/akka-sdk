package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import counter.application.CounterStore.CounterEntry;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;

import java.util.Optional;

// tag::seq-tracking[]
@ComponentId("counter-store-updater")
@Consume.FromEventSourcedEntity(CounterEntity.class)
public class CounterStoreUpdater extends Consumer {

  private final CounterStore counterStore;

  // end::seq-tracking[]
  public CounterStoreUpdater(CounterStore counterStore) {
    this.counterStore = counterStore;
  }

  // tag::seq-tracking[]
  public Effect onEvent(CounterEvent counterEvent) {
    var counterId = messageContext().eventSubject().get();
    var newSeqNum = messageContext().metadata().asCloudEvent().sequence();

    return effects().asyncEffect(
      counterStore.getById(counterId) // <1>
        .thenApply(counterEntry -> {
          var currentSeqNum = counterEntry.map(CounterEntry::seqNum).orElse(0L);
          if (!newSeqNum.isPresent()) { // <2>
            // missing sequence number, can't deduplicate
            return processEvent(counterEvent, counterEntry, 0L);
          } else {
            if (newSeqNum.get() <= currentSeqNum) {
              //duplicate, can be ignored
              return effects().ignore(); // <3>
            } else {
              // not a duplicate
              return processEvent(counterEvent, counterEntry, newSeqNum.get()); // <4>
            }
          }
        })
    );
  }

  private Effect processEvent(CounterEvent counterEvent, Optional<CounterEntry> currentEntry, Long seqNum) {
    var counterId = messageContext().eventSubject().get();
    var currentValue = currentEntry.map(CounterEntry::value).orElse(0);
    return switch (counterEvent) {
      case ValueIncreased increased -> {
        var updatedEntry = new CounterEntry(counterId, currentValue + increased.value(), seqNum);
        yield effects().asyncDone(counterStore.save(updatedEntry)); // <5>
      }
      case ValueMultiplied multiplied -> {
        var updatedEntry = new CounterEntry(counterId, currentValue * multiplied.multiplier(), seqNum);
        yield effects().asyncDone(counterStore.save(updatedEntry)); // <5>
      }
    };
  }
}
// end::seq-tracking[]
