/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.tracecontext;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import java.util.concurrent.Executor;

@Component(id = "trace-context-agent")
public class TraceContextAgent extends Agent {

  private final Executor executor;

  public TraceContextAgent(Executor executor) {
    this.executor = executor;
  }

  public Effect<SpanIds> observe() {
    return effects().reply(SpanIds.observe(context().tracing(), executor));
  }
}
