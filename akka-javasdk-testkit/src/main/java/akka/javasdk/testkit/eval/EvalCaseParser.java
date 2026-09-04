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
 * Turns recorded production interactions into runnable cases.
 *
 * <p>One JSON object per line:
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
 * <p>The spend figures are optional. When a line carries them they become budgets on the case: the
 * model call count as recorded, and tokens and latency with a slack factor, since a real model does
 * not spend the same twice. A line without them gets no budget.
 *
 * <p>For each recorded interaction: the stimulus is the recorded input; the world is a generated
 * setup that primes each bound stub with the tool results the recording carries; the expectations
 * are the recorded tool sequence and arguments — a <b>baseline</b> ("behaves like production did"),
 * not ground truth. A case worth asserting correctness on is promoted into the curated set after
 * review.
 *
 * <p>Argument drift: the stubs are primed by the arguments production saw. A call with other
 * arguments finds no canned result, gets a realistic tool error back, and stands in the evidence —
 * where the derived expectations fail it, which is the finding.
 */
public final class EvalCaseParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Half again what production spent, for tokens and latency. */
  public static final double DEFAULT_SLACK = 1.5;

  private EvalCaseParser() {}

  /**
   * Reads canonical interaction JSONL, one case per recorded interaction.
   *
   * <p>Refused at load time, with every problem and its line number: a line that does not parse, a
   * line with no {@code input}, and an interaction naming a tool with no binding.
   */
  public static List<EvalCase> parse(Path canonicalJsonl, ToolBindings bindings) {
    return parse(canonicalJsonl, bindings, DEFAULT_SLACK);
  }

  /**
   * As {@link #parse(Path, ToolBindings)}, with the slack the token and latency budgets allow over
   * what was recorded: {@code 1.5} holds a case to one and a half times production's spend, {@code
   * 1.0} to exactly it.
   */
  public static List<EvalCase> parse(Path canonicalJsonl, ToolBindings bindings, double slack) {
    if (slack < 1.0) throw new IllegalArgumentException("slack is at least 1.0, was " + slack);
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
        cases.add(readCase(MAPPER.readTree(line), lineNumber, bindings, slack));
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

  private static EvalCase readCase(
      JsonNode interaction, int lineNumber, ToolBindings bindings, double slack) {
    var input = interaction.path("input").asText("");
    if (input.isBlank()) throw new IllegalArgumentException("no input");
    var id = interaction.path("id").asText("replay-" + lineNumber);

    var recorded = new ArrayList<ToolBindings.RecordedCall>();
    for (var call : interaction.path("toolCalls")) {
      var tool = call.path("name").asText("");
      if (tool.isBlank()) throw new IllegalArgumentException("tool call with no name");
      if (!bindings.binds(tool)) {
        throw new IllegalArgumentException(
            "no binding for tool " + tool + "; bound: " + bindings.toolNames());
      }
      recorded.add(
          new ToolBindings.RecordedCall(tool, arguments(call), call.path("result").toString()));
    }

    Runnable setup = () -> recorded.forEach(call -> bindings.loaderFor(call.tool()).load(call));
    return new EvalCase(id, input, setup, withBudgets(baseline(recorded), interaction, slack));
  }

  /** The recorded spend, as ceilings. Refuses a figure that is not a count. */
  private static Expectations withBudgets(
      Expectations baseline, JsonNode interaction, double slack) {
    var expectations = baseline;
    var modelCalls = interaction.path("modelCalls");
    if (!modelCalls.isMissingNode()) {
      if (!modelCalls.canConvertToInt() || modelCalls.asInt() < 1)
        throw new IllegalArgumentException("modelCalls is not a positive count: " + modelCalls);
      expectations = expectations.modelCallsAtMost(modelCalls.asInt());
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
      expectations = expectations.tokensAtMost((long) Math.ceil(total * slack));
    }
    var latency = interaction.path("latencyMs");
    if (!latency.isMissingNode()) {
      if (!latency.canConvertToLong() || latency.asLong() < 1)
        throw new IllegalArgumentException("latencyMs is not a positive count: " + latency);
      expectations =
          expectations.latencyAtMost(Duration.ofMillis((long) Math.ceil(latency.asLong() * slack)));
    }
    return expectations;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> arguments(JsonNode call) {
    var argumentsNode = call.path("arguments");
    if (argumentsNode.isMissingNode() || argumentsNode.isNull()) return Map.of();
    return MAPPER.convertValue(argumentsNode, Map.class);
  }

  /** What production did, declared as expectations. A baseline, not ground truth. */
  private static Expectations baseline(List<ToolBindings.RecordedCall> recorded) {
    var expectations = Expectations.expect();
    if (recorded.isEmpty()) return expectations;

    var names = new LinkedHashSet<String>();
    var order = new ArrayList<String>();
    for (var call : recorded) {
      names.add(call.tool());
      order.add(call.tool());
    }
    expectations =
        expectations.tools(names.toArray(String[]::new)).toolOrder(order.toArray(String[]::new));
    for (var call : recorded) {
      for (var argument : call.arguments().entrySet()) {
        expectations =
            expectations.toolArgument(call.tool(), argument.getKey(), argument.getValue());
      }
    }
    return expectations;
  }
}
