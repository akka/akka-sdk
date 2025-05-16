package com.example.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import com.example.domain.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static akka.Done.done;


// tag::declarations[]
@ComponentId("counter") // <1>
public class CounterEntity extends KeyValueEntity<Counter> { // <2>

  private final String entityId;

  public CounterEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId(); // <3>
  }

  @Override
  public Counter emptyState() { return new Counter(0); } // <4>
  // end::declarations[]

  // tag::increase[]
  public Effect<Counter> increaseBy(int increaseBy) {
    Counter newCounter = currentState().increment(increaseBy); // <6>
    return effects()
        .updateState(newCounter) // <7>
        .thenReply(newCounter);
  }
  // end::increase[]

  // tag::behaviour[]
  public Effect<Counter> set(int number) {
    Counter newCounter = new Counter(number);
    return effects()
        .updateState(newCounter) // <1>
        .thenReply(newCounter); // <2>
  }

  public Effect<Counter> plusOne() {
    Counter newCounter = currentState().increment(1); // <3>
    return effects()
        .updateState(newCounter) // <4>
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
  public ReadOnlyEffect<Counter> get() {
    return effects()
        .reply(currentState()); // <1>
  }
  // end::query[]

  // tag::declarations[]
}
// end::declarations[]
