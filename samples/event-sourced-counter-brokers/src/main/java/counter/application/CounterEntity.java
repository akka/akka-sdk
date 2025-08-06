package counter.application;

import static counter.domain.CounterEvent.ValueIncreased;
import static counter.domain.CounterEvent.ValueMultiplied;
import static java.util.function.Function.identity;

import akka.javasdk.CommandException;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter")
public class CounterEntity extends EventSourcedEntity<Integer, CounterEvent> {

  private Logger logger = LoggerFactory.getLogger(CounterEntity.class);

  @Override
  public Integer emptyState() {
    return 0;
  }

  public Effect<Integer> increase(Integer value) {
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(identity());
  }

  //tag::increaseWithError[]
  public Effect<Integer> increaseWithError(Integer value) {
    if (currentState() + value > 10000) {
      return effects().error("Increasing the counter above 10000 is blocked"); // <1>
    }
    //end::increaseWithError[]
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    //tag::increaseWithError[]
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(identity());
  }

  //end::increaseWithError[]

  //tag::increaseWithException[]
  public static class CounterLimitExceededException extends CommandException { // <1>

    private final Integer value;

    public CounterLimitExceededException(Integer value) {
      super("Increasing the counter above 10000 is blocked");
      this.value = value;
    }

    public Integer getValue() {
      return value;
    }
  }

  public Effect<Integer> increaseWithException(Integer value) {
    if (currentState() + value > 10000) {
      throw new CounterLimitExceededException(value); // <2>
    }
    //end::increaseWithException[]
    logger.info("Counter {} increased by {}", this.commandContext().entityId(), value);
    //tag::increaseWithException[]
    return effects()
      .persist(new ValueIncreased(value, currentState() + value))
      .thenReply(identity());
  }

  //end::increaseWithException[]

  public ReadOnlyEffect<Integer> get() {
    return effects().reply(currentState());
  }

  public Effect<Integer> multiply(Integer value) {
    logger.info("Counter {} multiplied by {}", this.commandContext().entityId(), value);
    return effects()
      .persist(new ValueMultiplied(value, currentState() * value))
      .thenReply(identity());
  }

  @Override
  public Integer applyEvent(CounterEvent event) {
    return switch (event) {
      case ValueIncreased evt -> evt.updatedValue();
      case ValueMultiplied evt -> evt.updatedValue();
    };
  }
}
