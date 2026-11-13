/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Component(id = "trace-context-consumer")
@Consume.FromKeyValueEntity(TraceContextKvEntity.class)
public class TraceContextConsumer extends Consumer {

  public static final ConcurrentHashMap<String, SpanIds> observed = new ConcurrentHashMap<>();

  private final Executor executor;

  public TraceContextConsumer(Executor executor) {
    this.executor = executor;
  }

  public Effect onUpdate(String entityId) {
    observed.put(entityId, SpanIds.observe(messageContext().tracing(), executor));
    return effects().done();
  }
}
