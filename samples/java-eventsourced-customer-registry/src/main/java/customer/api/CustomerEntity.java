package customer.api;

import customer.domain.Address;
import customer.domain.Customer;
import customer.domain.CustomerEvent;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static customer.domain.CustomerEvent.*;


@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@ComponentId("customer")
public class CustomerEntity extends EventSourcedEntity<Customer, CustomerEvent> {
  private static final Logger logger = LoggerFactory.getLogger(CustomerEntity.class);


  record Confirm(String msg) {
    public static Confirm done = new Confirm("done");
  }

  public ReadOnlyEffect<Customer> getCustomer() {
    return effects().reply(currentState());
  }

  public Effect<Confirm> create(Customer customer) {
    logger.info("Creating {}", customer);
    return effects()
      .persist(new CustomerCreated(customer.email(), customer.name(), customer.address()))
      .thenReply(__ -> Confirm.done);
  }

  public Effect<Confirm> changeName(String newName) {

    return effects()
      .persist(new NameChanged(newName))
      .thenReply(__ -> Confirm.done);
  }


  public Effect<Confirm> changeAddress(Address newAddress) {
    return effects()
      .persist(new AddressChanged(newAddress))
      .thenReply(__ -> Confirm.done);
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
