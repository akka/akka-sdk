package com.example;

import akka.platform.javasdk.valueentity.ValueEntity;
import akka.platform.javasdk.annotations.TypeId;


// tag::declarations[]
@TypeId("counter") // <1>
public class CounterEntity extends ValueEntity<Integer> { // <3>

  @Override
  public Integer emptyState() { return 0; } // <4>
  // end::declarations[]

  // tag::increase[]
  public Effect<Number> increaseBy(Number increaseBy) {
    int newCounter = currentState() + increaseBy.value(); // <6>
    return effects()
        .updateState(newCounter) // <7>
        .thenReply(new Number(newCounter));
  }
  // end::increase[]

  // tag::behaviour[]
  public Effect<Number> set(Number number) {
    int newCounter = number.value();
    return effects()
        .updateState(newCounter) // <2>
        .thenReply(new Number(newCounter)); // <3>
  }

  public Effect<Number> plusOne() {
    int newCounter = currentState() + 1; // <5>
    return effects()
        .updateState(newCounter) // <6>
        .thenReply(new Number(newCounter));
  }
  // end::behaviour[]

  // tag::delete[]
  public Effect<String> delete() {
    return effects()
        .deleteEntity() // <1>
        .thenReply("deleted: " + commandContext().entityId());
  }
  // end::delete[]

  // tag::query[]
  public Effect<Number> get() {
    return effects()
        .reply(new Number(currentState())); // <2>
  }
  // end::query[]
  // tag::close[]

}
// end::close[]
