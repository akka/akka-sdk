package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@ComponentId("counter-journal-to-topic")
@Consume.FromEventSourcedEntity(CounterEntity.class) // <1>
@Produce.ToTopic("counter-events") // <2>
public class CounterJournalToTopicConsumer extends Consumer {

  // end::class[]
  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicConsumer.class);

  // tag::class[]
  public Effect onEvent(CounterEvent event) { // <3>
    // end::class[]
    logger.info("Received event: {}, publishing to topic counter-events", event.toString());
    // tag::class[]
    return effects().produce(event); // <4>
  }
}
// end::class[]