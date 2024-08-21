package customer.domain;

import akka.platform.javasdk.keyvalueentity.KeyValueEntity;
import akka.platform.javasdk.annotations.ComponentId;

@ComponentId("customer")
public class CustomerEntity extends KeyValueEntity<Customer> {


  public record Ok() {
    public static final Ok instance = new Ok();
  }

  public KeyValueEntity.Effect<Ok> create(Customer customer) {
    return effects().updateState(customer).thenReply(Ok.instance);
  }

  public KeyValueEntity.Effect<Customer> getCustomer() {
    return effects().reply(currentState());
  }

  public Effect<Ok> changeName(String newName) {
    Customer updatedCustomer = currentState().withName(newName);
    return effects().updateState(updatedCustomer).thenReply(Ok.instance);
  }

  public Effect<Ok> changeAddress(Address newAddress) {
    Customer updatedCustomer = currentState().withAddress(newAddress);
    return effects().updateState(updatedCustomer).thenReply(Ok.instance);
  }

}