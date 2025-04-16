package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::ese-consumer[]
@ComponentId("counter-events-consumer") // <1>
@Consume.FromEventSourcedEntity(CounterEntity.class) // <2>
public class CounterEventsConsumer extends Consumer { // <3>

  // end::ese-consumer[]
  private Logger logger = LoggerFactory.getLogger(CounterEventsConsumer.class);

  // tag::ese-consumer[]
  public Effect onEvent(CounterEvent event) { // <4>
    // end::ese-consumer[]
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), messageContext().metadata().asCloudEvent().id());
    // tag::ese-consumer[]
    return switch (event) {
      case ValueIncreased valueIncreased ->
        //processing value increased event
        effects().done(); // <5>
      case ValueMultiplied valueMultiplied -> effects().ignore(); // <6>
    };
  }
}
// end::ese-consumer[]