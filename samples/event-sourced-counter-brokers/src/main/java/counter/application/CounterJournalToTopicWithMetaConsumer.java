package counter.application;

import akka.javasdk.Metadata;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::class[]
@ComponentId("counter-journal-to-topic-with-meta")
@Consume.FromEventSourcedEntity(CounterEntity.class)
@Produce.ToTopic("counter-events-with-meta")  // <1>
public class CounterJournalToTopicWithMetaConsumer extends Consumer {

  // end::class[]
  private Logger logger = LoggerFactory.getLogger(CounterJournalToTopicWithMetaConsumer.class);

  // tag::class[]
  public Effect onEvent(CounterEvent event) {
    String counterId = messageContext().metadata().asCloudEvent().subject().get(); // <2>
    Metadata metadata = Metadata.EMPTY.add("ce-subject", counterId);
    logger.info("Received event for counter id {}: {}", counterId, event);
    return effects().produce(event, metadata); // <3>
  }
}
// end::class[]