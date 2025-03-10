package counter.application;

import akka.Done;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static akka.Done.done;
import static java.util.concurrent.CompletableFuture.completedFuture;

// tag::seq-tracking[]
public class CounterStore{

  public record CounterEntry(String counterId, int value, long seqNum) { // <1>
  }

  private Map<String, CounterEntry> store = new ConcurrentHashMap<>();


  public CompletionStage<Optional<CounterEntry>> getById(String counterId) {
    return completedFuture(Optional.ofNullable(store.get(counterId)));
  }

  public CompletionStage<Done> save(CounterEntry counterEntry) {
    return completedFuture(store.put(counterEntry.counterId(), counterEntry)).thenApply(__ -> done());
  }

  public CompletionStage<Collection<CounterEntry>> getAll() {
    return completedFuture(store.values());
  }
}
// end::seq-tracking[]
