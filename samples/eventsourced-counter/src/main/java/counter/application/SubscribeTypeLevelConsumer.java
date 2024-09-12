package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent.ValueIncreased;
import akka.javasdk.annotations.Consume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("subscribe-type-level")
@Consume.FromEventSourcedEntity(value = CounterEntity.class, ignoreUnknown = true) // <1>
public class SubscribeTypeLevelConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(SubscribeTypeLevelConsumer.class);

  public Effect onIncrease(ValueIncreased event) { // <2>
    logger.info("Received increased event: {} (msg ce id {})", event.toString(), messageContext().metadata().asCloudEvent().id());
    return effects().done(); // <3>
  }
}
// end::class[]