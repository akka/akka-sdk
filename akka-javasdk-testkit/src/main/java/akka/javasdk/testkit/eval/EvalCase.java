/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One evaluation case: the command sent to the agent and the evaluators its reply and tool calls
 * are checked with.
 *
 * <p>The command is what the agent's command handler takes. It is a String for a handler that takes
 * the user message directly, or the handler's own type, such as a record with several fields. All
 * cases of one experiment have the same command type.
 *
 * <pre>{@code
 * EvalCase.of("tier", "Which tier is cust_1 on?", Evaluators.shouldCallTools("getCustomer"));
 *
 * EvalCase.of(
 *     "tier-of-the-caller",
 *     new Ask("cust_1", "Which tier am I on?"),
 *     Evaluators.shouldCallTools("getCustomer"));
 * }</pre>
 *
 * <p>The stubs the agent's tools call are prepared before the experiment runs, in the test. A case
 * derived from a recording also carries the tool calls production made, and the runner loads their
 * results into the stubs through the {@link ToolBindings} given to it before the case's turn.
 *
 * @param <C> the command handler's parameter type
 * @param id unique within the suite; names the case in the report
 * @param command the command sent to the agent
 * @param recordedCalls the tool calls a recording carried, in recorded order. Empty for a case
 *     written by hand
 * @param evaluators the checks over the reply and the tool calls: built-ins from {@link
 *     Evaluators}, a {@link Judge} criterion, or a custom {@link Evaluator}. Empty when the case
 *     only collects evidence
 */
public record EvalCase<C>(
    String id, C command, List<RecordedCall> recordedCalls, List<Evaluator> evaluators) {

  public EvalCase {

    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");

    if (command == null) throw new IllegalArgumentException("command required");

    if (command instanceof String text && text.isBlank())
      throw new IllegalArgumentException("command required");

    if (recordedCalls == null) throw new IllegalArgumentException("recordedCalls required");

    if (recordedCalls.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("recorded call required");

    if (evaluators == null) throw new IllegalArgumentException("evaluators required");

    if (evaluators.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("evaluator required");

    recordedCalls = List.copyOf(recordedCalls);
    evaluators = List.copyOf(evaluators);
  }

  public static <C> EvalCase<C> of(String id, C command, Evaluator... evaluators) {
    return new EvalCase<>(id, command, List.of(), List.of(evaluators));
  }

  /**
   * The command as the text a {@link Judge} reads: a String command as is, any other command as
   * JSON. This is what the {@link Interaction} carries as its input.
   */
  public String commandText() {
    return Interaction.asText(command);
  }

  /** The same case with these evaluators added. */
  public EvalCase<C> withEvaluators(Evaluator... more) {
    var next = new ArrayList<>(evaluators);
    next.addAll(List.of(more));
    return new EvalCase<>(id, command, recordedCalls, next);
  }
}
