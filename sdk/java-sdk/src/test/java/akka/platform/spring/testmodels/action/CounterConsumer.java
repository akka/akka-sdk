/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.platform.spring.testmodels.action;

import akka.platform.javasdk.annotations.ComponentId;
import akka.platform.javasdk.annotations.Consume;
import akka.platform.javasdk.consumer.Consumer;
import akka.platform.spring.testmodels.keyvalueentity.Counter;
import akka.platform.spring.testmodels.keyvalueentity.CounterState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("counter-subscriber")
@Consume.FromKeyValueEntity(Counter.class)
public class CounterConsumer extends Consumer {

  private Logger logger = LoggerFactory.getLogger(getClass());

  public Effect changes(CounterState counterState) {
    logger.info("Counter subscriber: counter id '{}' is '{}'", counterState.id, counterState.value);
    return effects().done();
  }
}
