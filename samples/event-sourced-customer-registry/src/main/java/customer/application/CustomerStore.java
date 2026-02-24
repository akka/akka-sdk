package customer.application;

import customer.domain.Customer;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A simple customer store implementation used for idempotent updates documentation.
 */
// tag::idempotent-update[]
public class CustomerStore {

  // end::idempotent-update[]
  private Map<String, Customer> store = new ConcurrentHashMap<>();

  // tag::idempotent-update[]
  public Optional<Customer> getById(String customerId) {
    // end::idempotent-update[]
    return Optional.ofNullable(store.get(customerId));
    // tag::idempotent-update[]
  }

  public void save(String customerId, Customer customer) {
    // end::idempotent-update[]
    store.put(customerId, customer);
    // tag::idempotent-update[]
  }

  // end::idempotent-update[]

  public Collection<Customer> getAll() {
    return store.values();
  }
  // tag::idempotent-update[]
}
// end::idempotent-update[]
