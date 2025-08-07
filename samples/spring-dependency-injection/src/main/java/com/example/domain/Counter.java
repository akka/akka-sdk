package com.example.domain;

import static com.example.domain.CounterEvent.ValueIncreased;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter")
public class Counter extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(Counter.class);

  private final Clock clock;

  //injecting custom dependency to the Akka component
  public Counter(Clock clock) {
    this.clock = clock;
  }

  @Override
  public Integer emptyState() {
    return 0;
  }

  public Effect<String> increase(Integer value) {
    //accept updates only after 12:00
    if (clock.now().getHour() > 12) {
      logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
      return effects().persist(new ValueIncreased(value)).thenReply(Object::toString);
    } else {
      logger.info("Counter {} ignored increase {}", this.commandContext().entityId(), value);
      return effects().reply("ignored");
    }
  }

  public ReadOnlyEffect<Integer> get() {
    return effects().reply(currentState());
  }

  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> currentState() + evt.value();
    };
  }
}
