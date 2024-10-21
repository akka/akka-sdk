package counter.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import counter.domain.CounterEvent.ValueIncreased;
import counter.domain.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::topic-consumer[]
@ComponentId("counter-events-topic-consumer")
@Consume.FromTopic(value = "counter-events") // <1>
public class CounterEventsTopicConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CounterEventsTopicConsumer.class);

  public Effect onValueIncreased(ValueIncreased event) { // <2>
    logger.info("Received increased event: " + event.toString());
    return effects().done(); // <3>
  }

  public Effect onValueMultiplied(ValueMultiplied event) { // <2>
    logger.info("Received multiplied event: " + event.toString());
    return effects().done();
  }
}
// end::topic-consumer[]