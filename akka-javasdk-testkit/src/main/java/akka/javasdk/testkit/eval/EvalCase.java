/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One evaluation case: a stimulus, the world it assumes, and what to assert.
 *
 * <p>The runner cannot tell a curated case from a replayed one; both arrive as this type.
 *
 * @param id stable, unique within the suite; names the case in the report
 * @param userMessage the graded turn's input
 * @param setup primes the world before the turn: preload recording stubs, seed entities. Plain
 *     code, so it can close over whatever the test class holds. {@link #NO_SETUP} for a case that
 *     assumes nothing
 * @param expectations what the reply and the recorded tool calls are held against
 */
public record EvalCase(String id, String userMessage, Runnable setup, Expectations expectations) {

  public static final Runnable NO_SETUP = () -> {};

  public EvalCase {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");
    if (userMessage == null || userMessage.isBlank())
      throw new IllegalArgumentException("userMessage required");
    setup = setup == null ? NO_SETUP : setup;
    expectations = expectations == null ? Expectations.none() : expectations;
  }

  /** A case that starts from nothing. */
  public static EvalCase of(String id, String userMessage, Expectations expectations) {
    return new EvalCase(id, userMessage, NO_SETUP, expectations);
  }
}
