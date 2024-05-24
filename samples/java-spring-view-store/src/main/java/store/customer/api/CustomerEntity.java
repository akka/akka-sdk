package store.customer.api;

import store.customer.domain.Address;
import store.customer.domain.Customer;
import kalix.javasdk.eventsourcedentity.EventSourcedEntity;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.annotations.EventHandler;
import store.customer.domain.CustomerEvent;

import static store.customer.domain.CustomerEvent.*;

@TypeId("customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {

  public Effect<Customer> get() {
    return effects().reply(currentState());
  }

  public Effect<String> create(Customer customer) {
    return effects()
        .emitEvent(new CustomerCreated(customer.email(), customer.name(), customer.address()))
        .thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvent(CustomerCreated created) {
    return new Customer(created.email(), created.name(), created.address());
  }

  public Effect<String> changeName(String newName) {
    return effects().emitEvent(new CustomerNameChanged(newName)).thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvent(CustomerNameChanged customerNameChanged) {
    return currentState().withName(customerNameChanged.newName());
  }

  public Effect<String> changeAddress(Address newAddress) {
    return effects().emitEvent(new CustomerAddressChanged(newAddress)).thenReply(__ -> "OK");
  }

  @EventHandler
  public Customer onEvent(CustomerAddressChanged customerAddressChanged) {
    return currentState().withAddress(customerAddressChanged.newAddress());
  }
}
