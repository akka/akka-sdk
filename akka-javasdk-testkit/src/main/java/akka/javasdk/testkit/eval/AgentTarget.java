/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.Agent;
import akka.javasdk.testkit.TestKit;
import java.util.List;
import java.util.function.Function;

/**
 * The target behind {@link ExperimentRunner#forAgent}: calls the agent's command handler through
 * the TestKit's component client, in the turn's session, and reads the tool calls back from the
 * trace the runtime recorded for that session.
 *
 * @param <A> the agent
 * @param <C> the command handler's parameter type
 * @param <R> the command handler's reply type
 */
final class AgentTarget<A extends Agent, C, R> implements EvalTarget {

  private final TestKit testKit;
  private final Function2<A, C, Agent.Effect<R>> method;
  private final Function<String, C> command;
  private final Function<R, String> replyText;
  private final TracedTurns traced;

  AgentTarget(
      TestKit testKit,
      Function2<A, C, Agent.Effect<R>> method,
      Function<String, C> command,
      Function<R, String> replyText) {
    if (testKit == null) throw new IllegalArgumentException("testKit required");
    if (method == null) throw new IllegalArgumentException("agent method required");
    if (command == null) throw new IllegalArgumentException("command mapping required");
    if (replyText == null) throw new IllegalArgumentException("reply mapping required");
    this.testKit = testKit;
    this.method = method;
    this.command = command;
    this.replyText = replyText;
    this.traced = TracedTurns.from(testKit.getInMemorySpanExporter());
  }

  /** A reply is read as itself when it is text, and as its JSON otherwise. */
  static <R> String asText(R reply) {
    if (reply == null) return "";
    if (reply instanceof String text) return text;
    return JsonSupport.encodeToString(reply);
  }

  @Override
  public Outcome call(Turn turn) {
    R reply;
    try {
      reply =
          testKit
              .getComponentClient()
              .forAgent()
              .inSession(turn.sessionId())
              .method(method)
              .invoke(command.apply(turn.userMessage()));
    } catch (RuntimeException e) {
      return Outcome.failed(e, toolCallsOrNone(turn));
    }
    return Outcome.answered(
        new Interaction(replyText.apply(reply), traced.forSession(turn.sessionId())));
  }

  /** After a failure the trace may hold nothing for the session; the failure is the finding. */
  private List<ToolCall> toolCallsOrNone(Turn turn) {
    try {
      return traced.forSession(turn.sessionId()).toolCalls();
    } catch (RuntimeException e) {
      return List.of();
    }
  }
}
