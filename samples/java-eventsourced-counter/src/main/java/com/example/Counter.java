package com.example;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.example.CounterEvent.ValueIncreased;
import static com.example.CounterEvent.ValueMultiplied;

@ComponentId("counter")
public class Counter extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(Counter.class);

  @Override
  public Integer emptyState() {
    return 0;
  }


  public Effect<String> increase(Integer value) {
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueIncreased(value))
      .thenReply(Object::toString);
  }

  public Effect<Integer> get() {
    return effects().reply(currentState());
  }

  public Effect<String> multiply(Integer value) {
    logger.info("Counter {} multiplied by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueMultiplied(value))
      .thenReply(Object::toString);
  }

  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> currentState() + evt.value();
      case ValueMultiplied evt -> currentState() * evt.value();
    };
  }
}

