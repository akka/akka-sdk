/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;

/**
 * One evaluation case: the command sent to the agent. What the agent must do with it is checked by
 * the {@link akka.javasdk.evaluation.Evaluator} components bound to the agent in the configuration
 * of the test.
 *
 * <p>The command is what the agent's command handler takes. It is a String for a handler that takes
 * the user message directly, or the handler's own type, such as a record with several fields. All
 * cases of one experiment must have the same command type.
 *
 * <pre>{@code
 * EvalCase.of("tier", "Which tier is cust_1 on?");
 *
 * EvalCase.of("tier-of-the-caller", new Ask("cust_1", "Which tier am I on?"));
 * }</pre>
 *
 * @param <C> the command handler's parameter type
 * @param id unique within the experiment; names the case in the report
 * @param command the command sent to the agent
 */
public record EvalCase<C>(String id, C command) {

  public EvalCase {
    if (id == null || id.isBlank()) throw new IllegalArgumentException("case id required");
    if (command == null) throw new IllegalArgumentException("command required");
    if (command instanceof String text && text.isBlank())
      throw new IllegalArgumentException("command required");
  }

  public static <C> EvalCase<C> of(String id, C command) {
    return new EvalCase<>(id, command);
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
