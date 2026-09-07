/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * One evaluation case: a message to the agent and the evaluators its reply and tool calls are
 * checked with.
 *
 * @param id unique within the suite; names the case in the report
 * @param userMessage the message sent to the agent
 * @param setup runs before the agent is called, for example to prime stubs or seed entities. {@link
 *     #NO_SETUP} when the case needs none
 * @param evaluators the checks over the reply and the tool calls: built-ins from {@link
 *     Evaluators}, a {@link Judge} criterion, or a custom {@link Evaluator}. Empty when the case
 *     only collects evidence
 */
public record EvalCase(String id, String userMessage, Runnable setup, List<Evaluator> evaluators) {

  public static final Runnable NO_SETUP = () -> {};

  public EvalCase {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");
    if (userMessage == null || userMessage.isBlank())
      throw new IllegalArgumentException("userMessage required");
    setup = setup == null ? NO_SETUP : setup;
    evaluators = evaluators == null ? List.of() : List.copyOf(evaluators);
    if (evaluators.stream().anyMatch(e -> e == null))
      throw new IllegalArgumentException("evaluator required");
  }

  /** A case with a setup and evaluators. */
  public EvalCase(String id, String userMessage, Runnable setup, Evaluator... evaluators) {
    this(id, userMessage, setup, evaluators == null ? List.of() : List.of(evaluators));
  }

  /** A case with no setup. */
  public static EvalCase of(String id, String userMessage, Evaluator... evaluators) {
    return new EvalCase(id, userMessage, NO_SETUP, evaluators);
  }

  /** The same case with these evaluators added. */
  public EvalCase withEvaluators(Evaluator... more) {
    var next = new ArrayList<>(evaluators);
    next.addAll(List.of(more));
    return new EvalCase(id, userMessage, setup, next);
  }
}
