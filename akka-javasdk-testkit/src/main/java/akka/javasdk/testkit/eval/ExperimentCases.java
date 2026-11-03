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
 */
@DoNotInherit
public interface ExperimentCases<C> {

  /**
   * The agent under test. Its command handler takes the case's command. The evaluators bound to the
   * agent in the configuration the TestKit runs with evaluate every attempt.
   *
   * @param method the agent's command handler, for example {@code SupportAgent::ask}
   * @throws IllegalArgumentException when an evaluator is bound to the agent with a sampling ratio
   *     below 1.0, since the experiment waits for every bound evaluator on every attempt
   */
  <A extends Agent, R> Experiment agent(Function2<A, C, Agent.Effect<R>> method);
}
