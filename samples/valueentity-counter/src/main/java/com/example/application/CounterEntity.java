package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;

import static akka.Done.done;


// tag::declarations[]
@ComponentId("counter") // <1>
public class CounterEntity extends KeyValueEntity<Integer> { // <3>

  @Override
  public Integer emptyState() { return 0; } // <4>
  // end::declarations[]

  // tag::increase[]
  public Effect<Integer> increaseBy(int increaseBy) {
    int newCounter = currentState() + increaseBy; // <6>
    return effects()
        .updateState(newCounter) // <7>
        .thenReply(newCounter);
  }
  // end::increase[]

  // tag::behaviour[]
  public Effect<Integer> set(int number) {
    int newCounter = number;
    return effects()
        .updateState(newCounter) // <2>
        .thenReply(newCounter); // <3>
  }

  public Effect<Integer> plusOne() {
    int newCounter = currentState() + 1; // <5>
    return effects()
        .updateState(newCounter) // <6>
        .thenReply(newCounter);
  }
  // end::behaviour[]

  // tag::delete[]
  public Effect<Done> delete() {
    return effects()
        .deleteEntity() // <1>
        .thenReply(done());
  }
  // end::delete[]

  // tag::query[]
  public Effect<Integer> get() {
    return effects()
        .reply(currentState()); // <2>
  }
  // end::query[]
  // tag::close[]

}
// end::close[]
