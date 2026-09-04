/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.List;

/**
 * A custom check over one case's evidence. The extension seam of {@link Expectations}.
 *
 * <p>Reads only what happened — the reply and the recorded tool calls — never the service. A
 * judge-backed evaluator lives here too: the consumer wraps its own judge agent and maps the
 * verdict to a finding.
 */
@FunctionalInterface
public interface Evaluator {

  EvalResult evaluate(EvalCase evalCase, Interaction interaction, List<ToolCall> toolCalls);

  /**
   * @param evaluator which check produced this, named in the report
   * @param detail why, written for the person reading a failed run
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
