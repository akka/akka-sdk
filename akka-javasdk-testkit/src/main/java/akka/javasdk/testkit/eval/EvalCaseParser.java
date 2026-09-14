/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns recorded production interactions into cases.
 *
 * <p>The input is JSONL, one interaction per line:
 *
 * <pre>{@code
 * { "id": "c1",
 *   "input": "Why was I charged a late fee on loan_5001?",
 *   "toolCalls": [
 *     { "name": "getLoan", "arguments": {"loanId": "loan_5001"},
 *       "result": {"status": "overdue", "fee": 12.50} } ],
 *   "output": "You were charged because loan_5001 is overdue...",
 *   "modelCalls": 2,
 *   "tokens": { "input": 640, "output": 85 },
 *   "latencyMs": 1400 }
 * }</pre>
 *
 * <p>The case sends the recorded input, carries the recorded tool calls for the runner to load into
 * the stubs through its {@link ToolBindings}, and expects the recorded tools, their order and their
 * arguments. These evaluators describe what production did. They are a baseline, not a statement of
 * correctness.
 *
 * <p>{@code modelCalls}, {@code tokens} and {@code latencyMs} are optional. When present they
 * become budgets on the case: the model call count as recorded, tokens and latency multiplied by
 * the tolerance. {@code tokens} is either a total or an object with {@code input} and {@code
 * output}.
 *
 * <p>{@code input} is the text an agent whose command handler takes a String is sent. For a handler
 * with its own command type, record {@code input} as the JSON of that command and name the type in
 * {@link #parse(Path, Class)}.
 */
public final class EvalCaseParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * Multiplies the recorded token and latency figures to set the budgets. A rerun almost never
   * spends the same as the recording, so the budgets allow 50% more.
   */
  public static final double DEFAULT_TOLERANCE = 1.5;

  private EvalCaseParser() {}

  /**
   * Reads the JSONL file with {@link #DEFAULT_TOLERANCE}, as cases sending the recorded {@code
   * input} text.
   *
   * @throws IllegalArgumentException listing every line that does not parse, has no {@code input},
   *     or names a tool call without a name
   */
  public static List<EvalCase<String>> parse(Path canonicalJsonl) {
    return parse(canonicalJsonl, String.class, DEFAULT_TOLERANCE);
  }

  /**
   * Reads the JSONL file, as cases sending the recorded {@code input} text. {@code tolerance}
   * multiplies the recorded token and latency figures to set the budgets. {@code 1.0} holds a case
   * to exactly what was recorded. Values below {@code 1.0} are rejected.
   */
  public static List<EvalCase<String>> parse(Path canonicalJsonl, double tolerance) {
    return parse(canonicalJsonl, String.class, tolerance);
  }

  /**
   * Reads the JSONL file with {@link #DEFAULT_TOLERANCE}, as cases sending the recorded {@code
   * input} read into the agent's command type.
   *
   * @param commandType the agent command handler's parameter type. {@code String.class} takes the
   *     recorded text, any other type is read from the recorded {@code input} JSON
   */
  public static <C> List<EvalCase<C>> parse(Path canonicalJsonl, Class<C> commandType) {
    return parse(canonicalJsonl, commandType, DEFAULT_TOLERANCE);
  }

  /**
   * Reads the JSONL file, as cases sending the recorded {@code input} read into the agent's command
   * type. {@code tolerance} multiplies the recorded token and latency figures to set the budgets.
   * {@code 1.0} holds a case to exactly what was recorded. Values below {@code 1.0} are rejected.
   */
  public static <C> List<EvalCase<C>> parse(
      Path canonicalJsonl, Class<C> commandType, double tolerance) {
    if (commandType == null) throw new IllegalArgumentException("commandType required");
    if (tolerance < 1.0)
      throw new IllegalArgumentException("tolerance must be at least 1.0, was " + tolerance);
    List<String> lines;
    try {
      lines = Files.readAllLines(canonicalJsonl);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + canonicalJsonl, e);
    }

    var cases = new ArrayList<EvalCase<C>>();
    var problems = new ArrayList<String>();
    for (int i = 0; i < lines.size(); i++) {
      var line = lines.get(i);
      if (line.isBlank()) continue;
      var lineNumber = i + 1;
      try {
        cases.add(readCase(MAPPER.readTree(line), lineNumber, commandType, tolerance));
      } catch (IllegalArgumentException | IOException e) {
        problems.add("line " + lineNumber + ": " + e.getMessage());
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException(
          canonicalJsonl + " is not replayable:\n  " + String.join("\n  ", problems));
    }
    return List.copyOf(cases);
  }

  private static <C> EvalCase<C> readCase(
      JsonNode interaction, int lineNumber, Class<C> commandType, double tolerance) {
    var command = command(interaction.path("input"), commandType);
    var id = interaction.path("id").asText("replay-" + lineNumber);

    var recorded = new ArrayList<RecordedCall>();
    for (var call : interaction.path("toolCalls")) {
      var tool = call.path("name").asText("");
      if (tool.isBlank()) throw new IllegalArgumentException("tool call with no name");
      recorded.add(new RecordedCall(tool, arguments(call), call.path("result").toString()));
    }

    var evaluators = baseline(recorded);
    evaluators.addAll(budgets(interaction, tolerance));
    return new EvalCase<>(id, command, recorded, evaluators);
  }

  /** The recorded input as the command the agent is sent. */
  private static <C> C command(JsonNode input, Class<C> commandType) {
    if (commandType == String.class) {
      var text = input.isValueNode() ? input.asText("") : "";
      if (text.isBlank()) throw new IllegalArgumentException("no input");
      return commandType.cast(text);
    }
    if (input.isMissingNode() || input.isNull()) throw new IllegalArgumentException("no input");
    try {
      return MAPPER.treeToValue(input, commandType);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "input is not a " + commandType.getSimpleName() + ": " + e.getOriginalMessage());
    }
  }

  /** Turns the recorded spend into budgets. */
  private static List<Evaluator> budgets(JsonNode interaction, double tolerance) {

    var evaluators = new ArrayList<Evaluator>();
    var modelCalls = interaction.path("modelCalls");

    if (!modelCalls.isMissingNode()) {
      if (!modelCalls.canConvertToInt() || modelCalls.asInt() < 1)
        throw new IllegalArgumentException("modelCalls is not a positive count: " + modelCalls);
      evaluators.add(Evaluators.modelCallsAtMost(modelCalls.asInt()));
    }
    var tokens = interaction.path("tokens");

    if (!tokens.isMissingNode()) {
      long total;
      if (tokens.isNumber()) {
        total = tokens.asLong();
      } else if (tokens.isObject()) {
        total = tokens.path("input").asLong(0) + tokens.path("output").asLong(0);
      } else {
        throw new IllegalArgumentException(
            "tokens is neither a count nor {input, output}: " + tokens);
      }
      if (total < 1)
        throw new IllegalArgumentException("tokens is not a positive count: " + tokens);
      evaluators.add(Evaluators.tokensAtMost((long) Math.ceil(total * tolerance)));
    }

    var latency = interaction.path("latencyMs");
    if (!latency.isMissingNode()) {
      if (!latency.canConvertToLong() || latency.asLong() < 1)
        throw new IllegalArgumentException("latencyMs is not a positive count: " + latency);

      evaluators.add(
          Evaluators.latencyAtMost(
              Duration.ofMillis((long) Math.ceil(latency.asLong() * tolerance))));
    }
    return evaluators;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> arguments(JsonNode call) {
    var argumentsNode = call.path("arguments");
    if (argumentsNode.isMissingNode() || argumentsNode.isNull()) return Map.of();
    return MAPPER.convertValue(argumentsNode, Map.class);
  }

  /** The recorded tools, their order and their arguments, as evaluators. */
  private static List<Evaluator> baseline(List<RecordedCall> recorded) {
    var evaluators = new ArrayList<Evaluator>();
    if (recorded.isEmpty()) return evaluators;

    var names = new LinkedHashSet<String>();
    var order = new ArrayList<String>();
    for (var call : recorded) {
      names.add(call.tool());
      order.add(call.tool());
    }
    evaluators.add(Evaluators.tools(names.toArray(String[]::new)));
    evaluators.add(Evaluators.toolOrder(order.toArray(String[]::new)));
    for (var call : recorded) {
      for (var argument : call.arguments().entrySet()) {
        evaluators.add(
            Evaluators.toolArgument(call.tool(), argument.getKey(), argument.getValue()));
      }
    }
    return evaluators;
  }
}
