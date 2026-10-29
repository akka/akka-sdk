/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import akka.javasdk.client.ComponentClient;
import java.util.Optional;

/**
 * Calls the agent's command handler through the component client in the turn's session.
 *
 * @param <A> the agent
 * @param <C> the command handler's parameter type
 * @param <R> the command handler's reply type
 */
final class AgentTarget<A extends Agent, C, R> implements EvalTarget<C> {

  private final ComponentClient componentClient;
  private final Function2<A, C, Agent.Effect<R>> method;

  AgentTarget(ComponentClient componentClient, Function2<A, C, Agent.Effect<R>> method) {
    if (componentClient == null) throw new IllegalArgumentException("componentClient required");
    if (method == null) throw new IllegalArgumentException("agent method required");
    this.componentClient = componentClient;
    this.method = method;
  }

  @Override
  public Outcome call(Turn<C> turn) {
    try {
      var reply =
          componentClient
              .forAgent()
              .inSession(turn.sessionId())
              .method(method)
              .withDetailedReply()
              .invoke(turn.command());
      return Outcome.answered(
          reply.interactionId(), Optional.ofNullable(reply.value()).map(EvalCase::asText));
    } catch (RuntimeException e) {
      return Outcome.failed(e);
    }
  }
}
