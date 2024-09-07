package customer.application;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;
import static customer.domain.CustomerEvent.AddressChanged;
import static customer.domain.CustomerEvent.CustomerCreated;
import static customer.domain.CustomerEvent.NameChanged;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@ComponentId("customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {
  private static final Logger logger = LoggerFactory.getLogger(CustomerEntity.class);

  public ReadOnlyEffect<Customer> getCustomer() {
    return effects().reply(currentState());
  }

  public Effect<Done> create(Customer customer) {
    logger.info("Creating {}", customer);
    return effects()
      .persist(new CustomerCreated(customer.email(), customer.name(), customer.address()))
      .thenReply(__ -> done());
  }

  public Effect<Done> changeName(String newName) {
    return effects()
      .persist(new NameChanged(newName))
      .thenReply(__ -> done());
  }

  public Effect<Done> changeAddress(Address newAddress) {
    return effects()
      .persist(new AddressChanged(newAddress))
      .thenReply(__ -> done());
  }

  @Override
  public Customer applyEvent(CustomerEvent event) {
    return switch (event) {
      case CustomerCreated created -> new Customer(created.email(), created.name(), created.address());
      case NameChanged nameChanged -> currentState().withName(nameChanged.newName());
      case AddressChanged addressChanged -> currentState().withAddress(addressChanged.address());
    };
  }
}
