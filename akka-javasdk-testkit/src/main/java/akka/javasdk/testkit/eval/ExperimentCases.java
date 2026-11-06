/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;

/**
 * The cases of an experiment, before the agent under test is named. Obtained from {@link
 * ExperimentRunner#cases}.
 *
 * <p>Not for user extension.
 *
 * @param <C> the command type of the cases, which the agent's command handler must take
 * @param <V> the reply type of the cases, which the agent's command handler must reply with; {@code
 *     Object} for cases without an expected reply
 */
@DoNotInherit
public interface ExperimentCases<C, V> {

  /**
   * Binds the cases to the agent under test. Its command handler takes the case's command and
   * replies with the type of the case's expected reply. The evaluators bound to the agent in the
   * configuration the TestKit runs with evaluate every attempt.
   *
   * @param <A> the agent type
   * @param <R> the reply type of the command handler. It is the reply type of the cases, or a
   *     subtype of it. Cases without an expected reply have {@code Object} as their reply type and
   *     accept a handler with any reply type.
   * @param method the agent's command handler, for example {@code SupportAgent::ask}
   * @return the experiment builder ready to configure and run
   * @throws IllegalArgumentException when {@code method} is null, when a binding names an evaluator
   *     id that no evaluator component of the service has, when a binding uses the reserved name
   *     {@link ExperimentRunner#TARGET} or {@link ExperimentRunner#EXPECTED}, or when an evaluator
   *     is bound to the agent with a sampling ratio below 1.0, since the experiment waits for every
   *     bound evaluator on every attempt
   */
  <A extends Agent, R extends V> Experiment<V> agent(Function2<A, C, Agent.Effect<R>> method);
}
