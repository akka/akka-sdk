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
    if (currentState() == null) {
      logger.info("Creating customer with id '{}'", commandContext().entityId());
      return effects()
          .updateState(customer) // <6>
          .thenReply(Done.done());  // <7>
    } else {
      return effects().error("Customer exists already");
    }
  }

  public ReadOnlyEffect<Customer> getCustomer() {
    if (currentState() == null)
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    else   
      return effects().reply(currentState());
  }

  public Effect<Done> changeName(String newName) {
    if (currentState() == null) {
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    } else {
      Customer updatedCustomer = currentState().withName(newName);
      return effects()
          .updateState(updatedCustomer)
          .thenReply(Done.done());
    }
  }

  public Effect<Done> changeAddress(Address newAddress) {
    if (currentState() == null) {
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    } else {
      Customer updatedCustomer = currentState().withAddress(newAddress);
      return effects().updateState(updatedCustomer).thenReply(Done.done());
    }
  }

  public Effect<Done> delete() {
    if (currentState() == null) {
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    } else {
      logger.info("Deleting customer with id '{}'", commandContext().entityId());
      return effects().deleteEntity().thenReply(Done.done());
    }
  }

}
// end::customer[]
