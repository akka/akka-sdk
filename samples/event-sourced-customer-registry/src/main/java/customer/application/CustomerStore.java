package customer.application;

import akka.Done;
import customer.domain.Customer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

import static akka.Done.done;
import static java.util.concurrent.CompletableFuture.completedFuture;

/**
 * A simple customer store implementation used for idempotent updates documentation.
 */
// tag::idempotent-update[]
public class CustomerStore {

  // end::idempotent-update[]
  private Map<String, Customer> store = new ConcurrentHashMap<>();

  // tag::idempotent-update[]
  public CompletionStage<Optional<Customer>> getById(String customerId) {
    // end::idempotent-update[]
    return completedFuture(Optional.ofNullable(store.get(customerId)));
    // tag::idempotent-update[]
  }

  public CompletionStage<Done> save(String customerId, Customer customer) {
    // end::idempotent-update[]
    return completedFuture(store.put(customerId, customer)).thenApply(__ -> done());
    // tag::idempotent-update[]
  }
  // end::idempotent-update[]

  public CompletionStage<Collection<Customer>> getAll() {
    return completedFuture(store.values());
  }
  // tag::idempotent-update[]
}
// end::idempotent-update[]
