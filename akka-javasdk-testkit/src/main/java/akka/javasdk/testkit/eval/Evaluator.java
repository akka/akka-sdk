/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.List;

/**
 * A custom check over one case's evidence, added through {@link Expectations#satisfies}.
 *
 * <p>An evaluator reads only the interaction and the tool calls. It never calls the service.
 */
@FunctionalInterface
public interface Evaluator {

  EvalResult evaluate(EvalCase evalCase, Interaction interaction, List<ToolCall> toolCalls);

  /**
   * @param evaluator the name of the check that produced this, printed in the report
   * @param detail the reason, printed under a failed case
   */
  record EvalResult(String evaluator, Verdict verdict, String detail) {

    public enum Verdict {
      PASS,
      FAIL,
      /** The evidence this check needs is absent; neither a pass nor a fail. */
      ABSTAIN
    }

    public static EvalResult pass(String evaluator) {
      return new EvalResult(evaluator, Verdict.PASS, "");
    }

    public static EvalResult fail(String evaluator, String detail) {
      return new EvalResult(evaluator, Verdict.FAIL, detail);
    }

    public static EvalResult abstain(String evaluator, String detail) {
      return new EvalResult(evaluator, Verdict.ABSTAIN, detail);
    }
  }
}
