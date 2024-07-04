package com.example.actions;

import com.example.Counter;
import akka.platform.javasdk.action.Action;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Consume.FromTopic(value = "counter-commands", ignoreUnknown = true)
public class CounterCommandFromTopicAction extends Action {

  public record IncreaseCounter(String counterId, int value) {
  }

  public record MultiplyCounter(String counterId, int value) {
  }

  private ComponentClient componentClient;

  public CounterCommandFromTopicAction(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  private Logger logger = LoggerFactory.getLogger(CounterCommandFromTopicAction.class);

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
