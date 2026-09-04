/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

/**
 * One evaluation case.
 *
 * @param id unique within the suite; names the case in the report
 * @param userMessage the message sent to the agent
 * @param setup runs before the agent is called, for example to prime stubs or seed entities. {@link
 *     #NO_SETUP} when the case needs none
 * @param expectations what the reply and the tool calls are checked against
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

  /** A case with no setup. */
  public static EvalCase of(String id, String userMessage, Expectations expectations) {
    return new EvalCase(id, userMessage, NO_SETUP, expectations);
  }
}
