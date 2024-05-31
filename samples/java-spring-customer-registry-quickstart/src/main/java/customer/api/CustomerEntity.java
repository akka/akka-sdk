/*
 * Copyright 2021 Lightbend Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package customer.api;

// tag::customer[]
import kalix.javasdk.valueentity.ValueEntity;
import kalix.javasdk.annotations.Id;
import kalix.javasdk.annotations.TypeId;
import io.grpc.Status;
import customer.domain.Address;
import customer.domain.Customer;

@TypeId("customer") // <1>
public class CustomerEntity extends ValueEntity<Customer> { // <4>

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
    Customer updatedCustomer = currentState().withName(newName);
    return effects()
            .updateState(updatedCustomer)
            .thenReply(Ok.instance);
  }

  public Effect<Ok> changeAddress(Address newAddress) {
    Customer updatedCustomer = currentState().withAddress(newAddress);
    return effects().updateState(updatedCustomer).thenReply(Ok.instance);
  }

}
// end::customer[]