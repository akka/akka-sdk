/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.eventsourcedentity.TestESEvent;
import akka.javasdk.eventsourcedentity.TestEventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.javasdk.annotations.ComponentId;

@ComponentId("tracing-action")
@Consume.FromEventSourcedEntity(value = TestEventSourcedEntity.class, ignoreUnknown = true)
public class TestTracing extends Consumer {

  Logger logger = LoggerFactory.getLogger(TestTracing.class);

  public Effect consume(TestESEvent.Event2 event) {
    logger.info("registering a logging event");
    return effects().produce(
        messageContext().metadata().traceContext().traceParent().orElse("not-found"));
  }
}
