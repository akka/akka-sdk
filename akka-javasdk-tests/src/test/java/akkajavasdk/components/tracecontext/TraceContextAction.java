/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.annotations.Component;
import akka.javasdk.timedaction.TimedAction;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Component(id = "trace-context-action")
public class TraceContextAction extends TimedAction {

  public static final ConcurrentHashMap<String, SpanIds> observed = new ConcurrentHashMap<>();

  private final Executor executor;

  public TraceContextAction(Executor executor) {
    this.executor = executor;
  }

  public Effect observe(String id) {
    observed.put(id, SpanIds.observe(commandContext().tracing(), executor));
    return effects().done();
  }
}
