/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.timedaction;

import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.eventsourcedentity.TestESEvent;
import akka.javasdk.eventsourcedentity.TestEventSourcedEntity;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import akka.javasdk.annotations.ComponentId;

@ComponentId("tracing-action")
@Consume.FromEventSourcedEntity(value = TestEventSourcedEntity.class, ignoreUnknown = true)
public class TestTracing extends Consumer {

  Logger logger = LoggerFactory.getLogger(TestTracing.class);

  public Effect consume(TestESEvent.Event2 event) {
    logger.info("registering a logging event");

    // test expects a w3c encoded trace parent so here are some hoops to get that
    // FIXME if this turns out to be a common need we could provide the w3c encoded traceparent from Tracing
    //       but for now leaving otel hoops to users is fine enough
    String[] w3cEncodedTraceParent = {"not-enabled"};
    messageContext().tracing().parentSpan().ifPresent(span -> {
      var contextWithSpan = Context.current().with(span);
      W3CTraceContextPropagator.getInstance().inject(contextWithSpan, null,
          (carrier, key, value) -> {
            if (key.equals("traceparent")) {
              w3cEncodedTraceParent[0] = value;
            }
          });
    });
    return effects().produce(w3cEncodedTraceParent[0]);
  }
}
