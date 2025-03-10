package customer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import customer.domain.Customer;
import customer.domain.CustomerEvent;
import customer.domain.CustomerEvent.AddressChanged;
import customer.domain.CustomerEvent.CustomerCreated;
import customer.domain.CustomerEvent.NameChanged;

// tag::idempotent-update[]
@ComponentId("customer-store-updater")
@Consume.FromEventSourcedEntity(CustomerEntity.class)
public class CustomerStoreUpdater extends Consumer {

  private final CustomerStore customerStore;

  public CustomerStoreUpdater(CustomerStore customerStore) {
    this.customerStore = customerStore;
  }

  public Effect onEffect(CustomerEvent event) { // <1>
    var customerId = messageContext().eventSubject().get();
    return switch (event) {
      case CustomerCreated created ->
        effects().asyncDone(customerStore.save(customerId, new Customer(created.email(), created.name(), created.address())));

      case NameChanged nameChanged ->
        effects().asyncDone(customerStore.getById(customerId).thenCompose(customer -> {
          if (customer.isPresent()) {
            return customerStore.save(customerId, customer.get().withName(nameChanged.newName()));
          } else {
            throw new IllegalStateException("Customer not found: " + customerId);
          }
        }));

      case AddressChanged addressChanged ->
        effects().asyncDone(customerStore.getById(customerId).thenCompose(customer -> {
          if (customer.isPresent()) {
            return customerStore.save(customerId, customer.get().withAddress(addressChanged.address()));
          } else {
            throw new IllegalStateException("Customer not found: " + customerId);
          }
        }));
    };
  }
}
// end::idempotent-update[]
