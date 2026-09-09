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
 */
@DoNotInherit
public interface ExperimentCases {

  /** An evaluator that runs on every case, in addition to the case's expectations. */
  ExperimentCases evaluator(Evaluator evaluator);

  /**
   * The stubs the recorded tool calls are loaded into. Needed when a case carries recorded calls,
   * such as one from {@link EvalCaseParser}. Every tool a case names must be bound.
   */
  ExperimentCases bindings(ToolBindings bindings);

  /**
   * The agent under test. The command handler takes the case's message as a String. A String reply
   * is used as is, any other reply is rendered as JSON.
   *
   * @param method the agent's command handler, for example {@code SupportAgent::ask}
   */
  <A extends Agent, R> Experiment agent(Function2<A, String, Agent.Effect<R>> method);

  /**
   * The agent under test, with a command handler that has its own command and reply types.
   *
   * @param method the agent's command handler
   * @param command builds the command from the case's message
   * @param replyText renders the reply as the text the expectations read
   */
  <A extends Agent, C, R> Experiment agent(
      Function2<A, C, Agent.Effect<R>> method,
      Function<String, C> command,
      Function<R, String> replyText);
}
