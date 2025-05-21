/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function;
import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;

/**
 * Not for user extension
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

}
