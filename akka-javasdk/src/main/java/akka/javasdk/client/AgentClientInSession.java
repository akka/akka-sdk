/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;

/**
 * Not for user extension or instantiation, returned by the SDK component client
 */
@DoNotInherit
public interface AgentClientInSession {

  /**
   * Pass in an Agent command handler method reference, e.g. {@code SummarizerAgent::summarizeSession}
   */
  <T, R> ComponentMethodRef<R> method(Function<T, Agent.Effect<R>> methodRef);

  /**
   * Pass in an Agent command handler method reference, e.g. {@code PlannerAgent::plan}
   */
  <T, A1, R> ComponentMethodRef1<A1, R> method(Function2<T, A1, Agent.Effect<R>> methodRef);

  /**
   * Pass in an Agent command handler method reference, e.g. {@code ExpertAgent::ask}
   */
  <T> ComponentStreamMethodRef<String> tokenStream(Function<T, Agent.StreamEffect> methodRef);

  /**
   * Pass in an Agent command handler method reference, e.g. {@code ExpertAgent::ask}
   *
   * @param <A1> the type of parameter expected by the call
   */
  <T, A1> ComponentStreamMethodRef1<A1, String> tokenStream(Function2<T, A1, Agent.StreamEffect> methodRef);


  /**
   * Agents can be called based on the id without knowing the exact agent class
   * or lambda of the agent method by using the {{@code dynamicCall}} of the component
   * client.
   *
   * <pre>{@code
   * var response =
   *   componentClient
   *     .forAgent().inSession(sessionId)
   *     .dynamicCall(agentId)
   *     .invoke(request);
   * }</pre>
   *
   * @param agentId the componentId of the agent
   * @param <A1> the type of parameter expected by the call
   * @param <R> the return type of the call
   */
  <A1, R> DynamicMethodRef<A1, R> dynamicCall(String agentId);
}
