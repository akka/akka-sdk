package customer.application;

// tag::customer[]

import akka.Done;
import customer.domain.Address;
import customer.domain.Customer;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ComponentId("customer") // <1>
public class CustomerEntity extends KeyValueEntity<Customer> { // <4>

  private static final Logger logger = LoggerFactory.getLogger(CustomerEntity.class);


  public Effect<Done> create(Customer customer) {
    if (currentState() == null)
      return effects()
        .updateState(customer) // <6>
        .thenReply(Done.done());  // <7>
    else
      return effects().error("Customer exists already");
  }

  public Effect<Customer> getCustomer() {
    if (currentState() == null)
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    else   
      return effects().reply(currentState());
  }

  public Effect<Done> changeName(String newName) {
    Customer updatedCustomer = currentState().withName(newName);
    return effects()
            .updateState(updatedCustomer)
            .thenReply(Done.done());
  }

  public Effect<Done> changeAddress(Address newAddress) {
    Customer updatedCustomer = currentState().withAddress(newAddress);
    return effects().updateState(updatedCustomer).thenReply(Done.done());
  }

  public Effect<Done> delete() {
    return effects().deleteEntity().thenReply(Done.done());
  }

}
// end::customer[]
