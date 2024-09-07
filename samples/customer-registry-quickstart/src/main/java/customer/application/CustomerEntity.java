package customer.application;

// tag::customer[]
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.annotations.ComponentId;
import customer.domain.Address;
import customer.domain.Customer;

@ComponentId("customer") // <1>
public class CustomerEntity extends KeyValueEntity<Customer> { // <4>

  public record Ok() {
    public static final Ok instance = new Ok();
  }

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
    if (currentState() == null)
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    else {
      Customer updatedCustomer = currentState().withName(newName);
      return effects()
          .updateState(updatedCustomer)
          .thenReply(Ok.instance);
    }
  }

  public Effect<Ok> changeAddress(Address newAddress) {
    if (currentState() == null)
      return effects().error(
          "No customer found for id '" + commandContext().entityId() + "'");
    else {
      Customer updatedCustomer = currentState().withAddress(newAddress);
      return effects().updateState(updatedCustomer).thenReply(Ok.instance);
    }
  }

}
// end::customer[]
