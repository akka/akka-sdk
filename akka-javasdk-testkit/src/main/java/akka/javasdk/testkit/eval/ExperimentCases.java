/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.annotation.DoNotInherit;
import akka.japi.function.Function2;
import akka.javasdk.agent.Agent;
import java.util.function.Function;

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
   * An evaluator that runs on every case, in addition to the case's expectations. Its class carries
   * an {@link EvalLabel}.
   */
  ExperimentCases<C> evaluator(Evaluator evaluator);

  /**
   * The stubs the recorded tool calls are loaded into. Needed when a case carries recorded calls,
   * such as one from {@link EvalCaseParser}. Every tool a case names must be bound.
   */
  ExperimentCases<C> bindings(ToolBindings bindings);

  /**
   * The agent under test. Its command handler takes the case's command. A String reply is used as
   * is, any other reply is rendered as JSON.
   *
   * @param method the agent's command handler, for example {@code SupportAgent::ask}
   */
  <A extends Agent, R> Experiment agent(Function2<A, C, Agent.Effect<R>> method);

  /**
   * The agent under test. {@code replyText} renders the reply instead of the default JSON.
   *
   * @param method the agent's command handler
   * @param replyText renders the reply as the text the evaluators read
   */
  <A extends Agent, R> Experiment agent(
      Function2<A, C, Agent.Effect<R>> method, Function<R, String> replyText);
}
