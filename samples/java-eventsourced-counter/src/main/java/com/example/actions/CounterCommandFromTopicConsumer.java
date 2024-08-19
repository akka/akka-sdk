package com.example.actions;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;
import akka.platform.javasdk.consumer.Consumer;
import com.example.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-command-from-topic")
@Consume.FromTopic(value = "counter-commands", ignoreUnknown = true)
public class CounterCommandFromTopicConsumer extends Consumer {

  public record IncreaseCounter(String counterId, int value) {
  }

  public record MultiplyCounter(String counterId, int value) {
  }

  private ComponentClient componentClient;

  public CounterCommandFromTopicConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  private Logger logger = LoggerFactory.getLogger(CounterCommandFromTopicConsumer.class);

  public Effect<String> onValueIncreased(IncreaseCounter increase) {
    logger.info("Received increase event: {}", increase.toString());
    var increaseReply =
      componentClient.forEventSourcedEntity(increase.counterId)
        .method(Counter::increase)
        .invokeAsync(increase.value);
    return effects().asyncReply(increaseReply);
  }

  public Effect<String> onValueMultiplied(MultiplyCounter multiply) {
    logger.info("Received multiply event: {}", multiply.toString());
    var increaseReply =
      componentClient.forEventSourcedEntity(multiply.counterId)
        .method(Counter::multiply)
        .invokeAsync(multiply.value);
    return effects().asyncReply(increaseReply);
  }
}
