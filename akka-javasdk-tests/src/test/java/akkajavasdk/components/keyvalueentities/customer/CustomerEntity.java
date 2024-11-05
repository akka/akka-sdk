/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.keyvalueentities.customer;

import akkajavasdk.Ok;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import java.time.Instant;

@ComponentId("customer")
public class CustomerEntity extends KeyValueEntity<CustomerEntity.Customer> {

  public record Customer(String name, Instant createdOn) {
  }

  public Effect<Ok> create(Customer customer) {
    return effects().updateState(customer).thenReply(Ok.instance);
  }

  public Effect<CustomerEntity.Customer> get() {
    return effects().reply(currentState());
  }
}
