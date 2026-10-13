/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/** Scripted targets for the runner's and the gate's tests. */
final class Targets {

  private Targets() {}

  /**
   * Answers with the text {@code reply} gives for the call number, counted from 1 over all cases
   * and runs, and the case id.
   */
  static EvalTarget<String> replyingByCall(BiFunction<Integer, String, String> reply) {
    var calls = new AtomicInteger();
    return turn ->
        EvalTarget.Outcome.answered(
            Interaction.of(turn.command(), reply.apply(calls.incrementAndGet(), turn.caseId())));
  }
}
