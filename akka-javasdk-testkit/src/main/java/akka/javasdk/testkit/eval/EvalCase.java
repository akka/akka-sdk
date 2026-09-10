/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One evaluation case: a message to the agent and the evaluators its reply and tool calls are
 * checked with.
 *
 * <p>The stubs the agent's tools call are prepared before the experiment runs, in the test. A case
 * derived from a recording also carries the tool calls production made, and the runner loads their
 * results into the stubs through the {@link ToolBindings} given to it before the case's turn.
 *
 * @param id unique within the suite; names the case in the report
 * @param userMessage the message sent to the agent
 * @param recordedCalls the tool calls a recording carried, in recorded order. Empty for a case
 *     written by hand
 * @param evaluators the checks over the reply and the tool calls: built-ins from {@link
 *     Evaluators}, a {@link Judge} criterion, or a custom {@link Evaluator}. Empty when the case
 *     only collects evidence
 */
public record EvalCase(
    String id, String userMessage, List<RecordedCall> recordedCalls, List<Evaluator> evaluators) {

  public EvalCase {

    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");

    if (userMessage == null || userMessage.isBlank())
      throw new IllegalArgumentException("userMessage required");

    if (recordedCalls == null) throw new IllegalArgumentException("recordedCalls required");

    if (recordedCalls.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("recorded call required");

    if (evaluators == null) throw new IllegalArgumentException("evaluators required");

    if (evaluators.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("evaluator required");

    recordedCalls = List.copyOf(recordedCalls);
    evaluators = List.copyOf(evaluators);
  }

  public static EvalCase of(String id, String userMessage, Evaluator... evaluators) {
    return new EvalCase(id, userMessage, List.of(), List.of(evaluators));
  }

  /** The same case with these evaluators added. */
  public EvalCase withEvaluators(Evaluator... more) {
    var next = new ArrayList<>(evaluators);
    next.addAll(List.of(more));
    return new EvalCase(id, userMessage, recordedCalls, next);
  }
}
