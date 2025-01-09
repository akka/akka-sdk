package store.customer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import store.customer.domain.Address;
import store.customer.domain.Customer;
import store.customer.domain.CustomerEvent;

import static akka.Done.done;
import static store.customer.domain.CustomerEvent.CustomerAddressChanged;
import static store.customer.domain.CustomerEvent.CustomerCreated;
import static store.customer.domain.CustomerEvent.CustomerNameChanged;

@ComponentId("customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {

  public ReadOnlyEffect<Customer> get() {
    return effects().reply(currentState());
  }

  public Effect<Done> create(Customer customer) {
    return effects()
      .persist(new CustomerCreated(customer.email(), customer.name(), customer.address()))
      .thenReply(__ -> done());
  }

  public Effect<Done> changeName(String newName) {
    return effects().persist(new CustomerNameChanged(newName)).thenReply(__ -> done());
  }

  public Effect<Done> changeAddress(Address newAddress) {
    return effects().persist(new CustomerAddressChanged(newAddress)).thenReply(__ -> done());
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
