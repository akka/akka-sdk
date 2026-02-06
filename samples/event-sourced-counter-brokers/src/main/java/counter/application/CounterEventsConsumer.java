package counter.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.SnapshotHandler;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::ese-consumer[]
@Component(id = "counter-events-consumer") // <1>
@Consume.FromEventSourcedEntity(CounterEntity.class) // <2>
public class CounterEventsConsumer extends Consumer { // <3>

  // end::ese-consumer[]
  private Logger logger = LoggerFactory.getLogger(CounterEventsConsumer.class);

  // tag::ese-consumer-from-snapshot[]
  @SnapshotHandler
  public Effect onSnapshot(Integer value) {
    // end::ese-consumer-from-snapshot[]
    logger.info("Received snapshot: {} (entity id {})",
        value,
        messageContext().eventSubject().orElse(""));
    // tag::ese-consumer-from-snapshot[]
    return effects().done();
  }
  // end::ese-consumer-from-snapshot[]

  // tag::ese-consumer[]
  public Effect onEvent(CounterEvent event) { // <4>
    // end::ese-consumer[]
    logger.info(
      "Received increased event: {} (entity id {})",
      event,
      messageContext().eventSubject().orElse("")
    );
    // tag::ese-consumer[]
    return switch (event) {
      case ValueIncreased valueIncreased -> effects().done(); // <5>
      case ValueMultiplied valueMultiplied -> effects().ignore(); // <6>
    };
  }
}
// end::ese-consumer[]
