package counter.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// tag::seq-tracking[]
public class CounterStore {

  public record CounterEntry(String counterId, int value, long seqNum) {} // <1>

  private Map<String, CounterEntry> store = new ConcurrentHashMap<>();

  public Optional<CounterEntry> getById(String counterId) {
    return Optional.ofNullable(store.get(counterId));
  }

  public void save(CounterEntry counterEntry) {
    store.put(counterEntry.counterId(), counterEntry);
  }

  public Collection<CounterEntry> getAll() {
    return store.values();
  }
}
// end::seq-tracking[]
