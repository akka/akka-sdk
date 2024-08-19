package com.example.actions;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import com.example.CounterEvent.ValueIncreased;
import com.example.CounterEvent.ValueMultiplied;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-topic-subscription")
@Consume.FromTopic(value = "counter-events") // <1>
public class CounterTopicSubscriptionConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(CounterTopicSubscriptionConsumer.class);

  public Effect onValueIncreased(ValueIncreased event) { // <2>
    logger.info("Received increased event: " + event.toString());
    return effects().done(); // <3>
  }

  public Effect onValueMultiplied(ValueMultiplied event) { // <5>
    logger.info("Received multiplied event: " + event.toString());
    return effects().done(); // <6>
  }
}
// end::class[]