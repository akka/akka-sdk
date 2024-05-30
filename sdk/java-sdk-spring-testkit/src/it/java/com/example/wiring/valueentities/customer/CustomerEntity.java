/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package com.example.wiring.valueentities.customer;

import com.example.wiring.Ok;
import kalix.javasdk.annotations.TypeId;
import kalix.javasdk.valueentity.ValueEntity;

import java.time.Instant;

@TypeId("customer")
public class CustomerEntity extends ValueEntity<CustomerEntity.Customer> {

  public record Customer(String name, Instant createdOn) {
  }

  public Effect<Ok> create(Customer customer) {
    return effects().updateState(customer).thenReply(Ok.instance);
  }

  public Effect<CustomerEntity.Customer> get() {
    return effects().reply(currentState());
  }
}
