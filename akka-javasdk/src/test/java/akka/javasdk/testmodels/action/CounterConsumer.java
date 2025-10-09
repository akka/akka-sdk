/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.action;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.testmodels.keyvalueentity.Counter;
import akka.javasdk.testmodels.keyvalueentity.CounterState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "counter-subscriber")
@Consume.FromKeyValueEntity(Counter.class)
public class CounterConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect changes(CounterState counterState) {
    logger.info("Counter subscriber: counter id '{}' is '{}'", counterState.id, counterState.value);
    return effects().done();
  }
}
