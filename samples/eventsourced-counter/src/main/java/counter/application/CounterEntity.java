package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static counter.domain.CounterEvent.ValueIncreased;
import static counter.domain.CounterEvent.ValueMultiplied;

@ComponentId("counter")
public class CounterEntity extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(CounterEntity.class);

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

  public ReadOnlyEffect<Integer> get() {
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

