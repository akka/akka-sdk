/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

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
   * Reads the JSONL file with {@link #DEFAULT_TOLERANCE}.
   *
   * @throws IllegalArgumentException listing every line that does not parse, has no {@code input},
   *     or names a tool call without a name
   */
  public static List<EvalCase> parse(Path canonicalJsonl) {
    return parse(canonicalJsonl, DEFAULT_TOLERANCE);
  }

  /**
   * Reads the JSONL file. {@code tolerance} multiplies the recorded token and latency figures to
   * set the budgets. {@code 1.0} holds a case to exactly what was recorded. Values below {@code
   * 1.0} are rejected.
   */
  public static List<EvalCase> parse(Path canonicalJsonl, double tolerance) {
    if (tolerance < 1.0)
      throw new IllegalArgumentException("tolerance must be at least 1.0, was " + tolerance);
    List<String> lines;
    try {
      lines = Files.readAllLines(canonicalJsonl);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + canonicalJsonl, e);
    }

    var cases = new ArrayList<EvalCase>();
    var problems = new ArrayList<String>();
    for (int i = 0; i < lines.size(); i++) {
      var line = lines.get(i);
      if (line.isBlank()) continue;
      var lineNumber = i + 1;
      try {
        cases.add(readCase(MAPPER.readTree(line), lineNumber, tolerance));
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

  private static EvalCase readCase(JsonNode interaction, int lineNumber, double tolerance) {
    var input = interaction.path("input").asText("");
    if (input.isBlank()) throw new IllegalArgumentException("no input");
    var id = interaction.path("id").asText("replay-" + lineNumber);

    var recorded = new ArrayList<RecordedCall>();
    for (var call : interaction.path("toolCalls")) {
      var tool = call.path("name").asText("");
      if (tool.isBlank()) throw new IllegalArgumentException("tool call with no name");
      recorded.add(new RecordedCall(tool, arguments(call), call.path("result").toString()));
    }

    var evaluators = baseline(recorded);
    evaluators.addAll(budgets(interaction, tolerance));
    return new EvalCase(id, input, recorded, evaluators);
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
