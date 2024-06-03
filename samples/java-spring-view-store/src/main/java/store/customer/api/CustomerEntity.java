package store.customer.api;

import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.customer.domain.CustomerEvent;

import static store.customer.domain.CustomerEvent.CustomerAddressChanged;
import static store.customer.domain.CustomerEvent.CustomerCreated;
import static store.customer.domain.CustomerEvent.CustomerNameChanged;

@TypeId("customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {

  public Effect<Customer> get() {
    return effects().reply(currentState());
  }

  public Effect<String> create(Customer customer) {
    return effects()
      .persist(new CustomerCreated(customer.email(), customer.name(), customer.address()))
      .thenReply(__ -> "OK");
  }

  public Effect<String> changeName(String newName) {
    return effects().persist(new CustomerNameChanged(newName)).thenReply(__ -> "OK");
  }


  public Effect<String> changeAddress(Address newAddress) {
    return effects().persist(new CustomerAddressChanged(newAddress)).thenReply(__ -> "OK");
  }


  @Override
  public Customer applyEvent(CustomerEvent event) {
    return switch (event) {
      case CustomerCreated evt -> new Customer(evt.email(), evt.name(), evt.address());
      case CustomerNameChanged evt -> currentState().withName(evt.newName());
      case CustomerAddressChanged evt -> currentState().withAddress(evt.newAddress());
    };
  }
}
