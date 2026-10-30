/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.testkit.AgentTrace;
import akka.javasdk.testkit.GuardrailResult;
import akka.javasdk.testkit.GuardrailResult.Decision;
import akka.javasdk.testkit.TelemetryReader;
import akka.javasdk.testkit.TestKit;
import java.util.function.Function;

/**
 * Calls the agent's command handler through the TestKit component client in the turn's session, and
 * reads the evidence from the trace recorded for that session, also when the call failed. A failed
 * call that a guardrail denial stopped is a denied turn.
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
      return outcome(turn.commandText(), e, traceOrNone(turn));
    }
  }

  /**
   * The outcome of a failed call: denied when the failure names a guardrail that denied and the
   * trace holds no guardrail error, failed otherwise.
   */
  static Outcome outcome(String input, RuntimeException failure, AgentTrace trace) {
    var guardrails = trace.guardrails();

    var errored = guardrails.stream().anyMatch(g -> g.decision() == Decision.ERROR);
    var stoppedByDenial =
        guardrails.stream().anyMatch(g -> g.decision() == Decision.DENY && blockedBy(failure, g));

    if (stoppedByDenial && !errored) {
      return Outcome.denied(new Interaction(input, trace));
    }

    return Outcome.failed(failure, trace);
  }

  // The runtime fails a call that a guardrail denied with the message
  // "<boundary> guardrail blocked, category [<category>], name [<name>]: <reason>".
  private static boolean blockedBy(RuntimeException failure, GuardrailResult guardrail) {
    var message = failure.getMessage();
    if (message == null) return false;

    var label = "category [" + guardrail.category() + "], name [" + guardrail.name() + "]";
    return message.contains("guardrail blocked, " + label + ": ");
  }

  // After a failure the trace may hold nothing for the session.
  private AgentTrace traceOrNone(Turn<C> turn) {
    try {
      return telemetry.getAgentTrace(turn.sessionId());
    } catch (RuntimeException e) {
      return AgentTrace.NONE;
    }
  }
}
