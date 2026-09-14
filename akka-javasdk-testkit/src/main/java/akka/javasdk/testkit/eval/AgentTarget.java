/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.testkit.TelemetryReader;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.ToolCall;
import java.util.List;
import java.util.function.Function;

/**
 * Calls the agent's command handler through the TestKit component client in the turn's session, and
 * reads the tool calls from the trace recorded for that session.
 *
 * @param <A> the agent
 * @param <C> the command handler's parameter type
 * @param <R> the command handler's reply type
 */
final class AgentTarget<A extends Agent, C, R> implements EvalTarget<C> {

  private final TestKit testKit;
  private final Function2<A, C, Agent.Effect<R>> method;
  private final Function<R, String> replyText;
  private final TelemetryReader telemetry;

  AgentTarget(
      TestKit testKit, Function2<A, C, Agent.Effect<R>> method, Function<R, String> replyText) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    if (method == null) throw new IllegalArgumentException("agent method required");
    if (replyText == null) throw new IllegalArgumentException("reply mapping required");
    this.testKit = testKit;
    this.method = method;
    this.replyText = replyText;
    this.telemetry = new TelemetryReader(testKit.getInMemorySpanExporter());
  }

  @Override
  public Outcome call(Turn<C> turn) {
    try {
      R reply =
          testKit
              .getComponentClient()
              .forAgent()
              .inSession(turn.sessionId())
              .method(method)
              .invoke(turn.command());
      return Outcome.answered(
          new Interaction(
              turn.commandText(),
              replyText.apply(reply),
              telemetry.getAgentTrace(turn.sessionId())));
    } catch (RuntimeException e) {
      return Outcome.failed(e, toolCallsOrNone(turn));
    }
  }

  // After a failure the trace may hold nothing for the session.
  private List<ToolCall> toolCallsOrNone(Turn<C> turn) {
    try {
      return telemetry.getAgentTrace(turn.sessionId()).toolCalls();
    } catch (RuntimeException e) {
      return List.of();
    }
  }
}
