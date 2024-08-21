package customer.api;

// tag::customer[]

import customer.domain.Address;
import customer.domain.Customer;
import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.keyvalueentity.KeyValueEntity;

@ComponentId("customer") // <1>
public class CustomerEntity extends KeyValueEntity<Customer> { // <4>

  public Effect<Ok> create(Customer customer) {
    if (currentState() == null)
      return effects()
        .updateState(customer) // <6>
        .thenReply(Ok.instance);  // <7>
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

  public Effect<Ok> changeName(String newName) {
    Customer updatedCustomer = currentState().withName(newName);
    return effects()
            .updateState(updatedCustomer)
            .thenReply(Ok.instance);
  }

  public Effect<Ok> changeAddress(Address newAddress) {
    Customer updatedCustomer = currentState().withAddress(newAddress);
    return effects().updateState(updatedCustomer).thenReply(Ok.instance);
  }

  public Effect<Ok> delete() {
    return effects().deleteEntity().thenReply(Ok.instance);
  }

}
// end::customer[]
