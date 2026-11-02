/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import java.util.Optional;

/**
 * One evaluation case: the command sent to the agent, and optionally the reply the agent is
 * expected to give. What the agent must do with the command is checked by the {@link
 * akka.javasdk.evaluation.Evaluator} components bound to the agent in the configuration of the
 * test. A case with an expected reply is also graded by the runner, see {@link Experiment#grade}.
 *
 * <p>The command is what the agent's command handler takes. It is a String for a handler that takes
 * the user message directly, or the handler's own type, such as a record with several fields. All
 * cases of one experiment must have the same command type.
 *
 * <p>The expected reply is what the command handler returns: a String for a handler that replies
 * with the model text, or the type given to {@code responseConformsTo}. The expected reply is never
 * sent to the agent. Within one experiment, either every case has an expected reply or none has.
 *
 * <pre>{@code
 * EvalCase.of("tier", "Which tier is cust_1 on?");
 *
 * EvalCase.of("tier-of-the-caller", new Ask("cust_1", "Which tier am I on?"));
 *
 * EvalCase.of("refund-route", "I want my money back", new Route("open-refund"));
 * }</pre>
 *
 * <p>A case without an expected reply that is kept in a field is declared with {@code Object} as
 * the reply type, {@code EvalCase<String, Object>}, so it runs against an agent with any reply
 * type.
 *
 * @param <C> the command handler's parameter type
 * @param <V> the command handler's reply type
 * @param id unique within the experiment; names the case in the report
 * @param command the command sent to the agent
 * @param expected the reply the agent is expected to give; empty when the case has none
 */
public record EvalCase<C, V>(String id, C command, Optional<V> expected) {

  public EvalCase {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");
    if (command == null) throw new IllegalArgumentException("command required");
    if (command instanceof String text && text.isBlank())
      throw new IllegalArgumentException("command required");
    if (expected == null) throw new IllegalArgumentException("expected required");
  }

  /** A case without an expected reply. */
  public static <C, V> EvalCase<C, V> of(String id, C command) {
    return new EvalCase<>(id, command, Optional.empty());
  }

  /**
   * A case with an expected reply.
   *
   * @param expected the reply the agent is expected to give, not null
   */
  public static <C, V> EvalCase<C, V> of(String id, C command, V expected) {
    if (expected == null) throw new IllegalArgumentException("expected reply required");
    return new EvalCase<>(id, command, Optional.of(expected));
  }

  /** The command as text: a String command as is, any other command as JSON. */
  public String commandText() {
    return asText(command);
  }

  static String asText(Object value) {
    if (value == null) return "";
    if (value instanceof String text) return text;
    return JsonSupport.encodeToString(value);
  }
}
