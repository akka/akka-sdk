/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.testkit.GuardrailResult;
import akka.javasdk.testkit.ModelCall;
import akka.javasdk.testkit.ToolCall;
import akka.javasdk.testkit.eval.Evaluator.EvalResult;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import akka.javasdk.testkit.eval.ExperimentRunner.Rate;
import akka.javasdk.testkit.eval.ExperimentRunner.Report;
import akka.javasdk.testkit.eval.ExperimentRunner.Spend;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link Report} in the {@code akka-eval-report} JSON format. The format is described by
 * {@code eval-report.schema.json} next to this class on the classpath.
 */
final class EvalReportJson {

  static final String FORMAT = "akka-eval-report";
  static final int FORMAT_VERSION = 1;

  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private EvalReportJson() {}

  static String render(Report report) {
    var root = MAPPER.createObjectNode();
    root.put("format", FORMAT);
    root.put("formatVersion", FORMAT_VERSION);
    root.put("name", report.name());
    root.put("startedAt", report.startedAt().toString());
    root.put("finishedAt", report.finishedAt().toString());
    root.set("gate", gate(report));
    root.set("summary", summary(report));
    root.set("evaluators", evaluators(report.rates()));
    root.set("spend", spend(report.spend()));
    root.set("cases", cases(report.results()));
    try {
      return MAPPER.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not render the eval report as JSON", e);
    }
  }

  private static ObjectNode gate(Report report) {
    var gate = MAPPER.createObjectNode();
    gate.put("passed", report.verdict().passed());
    gate.put("detail", report.verdict().detail());
    return gate;
  }

  private static ObjectNode summary(Report report) {
    var passed = (int) report.results().stream().filter(CaseResult::passed).count();
    var summary = MAPPER.createObjectNode();
    summary.put("cases", report.results().size());
    summary.put("passedCases", passed);
    summary.put("failedCases", report.results().size() - passed);
    summary.put("passRate", report.passRate());
    return summary;
  }

  private static ArrayNode evaluators(Map<String, Rate> rates) {
    var evaluators = MAPPER.createArrayNode();
    rates.forEach(
        (label, rate) -> {
          var node = evaluators.addObject();
          node.put("evaluator", label);
          node.put("passed", rate.passed());
          node.put("failed", rate.failed());
          node.put("inconclusive", rate.inconclusive());
        });
    return evaluators;
  }

  private static ObjectNode spend(Spend spend) {
    var node = MAPPER.createObjectNode();
    node.put("casesWithEvidence", spend.casesWithEvidence());
    node.put("modelCalls", spend.modelCalls());
    node.put("inputTokens", spend.inputTokens());
    node.put("outputTokens", spend.outputTokens());
    node.put("latencyMs", spend.latencyMs());
    return node;
  }

  private static ArrayNode cases(List<CaseResult> results) {
    var cases = MAPPER.createArrayNode();
    for (var result : results) {
      var node = cases.addObject();
      node.put("id", result.caseId());
      node.put("passed", result.passed());
      node.set("interaction", interaction(result.interaction()));
      node.set("results", evalResults(result.evalResults()));
    }
    return cases;
  }

  private static ObjectNode interaction(Interaction interaction) {
    var node = MAPPER.createObjectNode();
    node.put("input", interaction.input());
    node.put("userMessage", interaction.userMessage());
    node.put("reply", interaction.reply());
    node.put("finalModelText", interaction.finalModelText());
    node.put("latencyMs", interaction.latency().toMillis());
    node.put("inputTokens", interaction.inputTokens());
    node.put("outputTokens", interaction.outputTokens());
    node.put("blocked", interaction.blocked());
    node.set("toolCalls", toolCalls(interaction.toolCalls()));
    node.set("modelCalls", modelCalls(interaction.modelCalls()));
    node.set("guardrails", guardrails(interaction.guardrails()));
    return node;
  }

  private static ArrayNode toolCalls(List<ToolCall> calls) {
    var array = MAPPER.createArrayNode();
    for (var call : calls) {
      var node = array.addObject();
      node.put("name", call.name());
      node.set("arguments", MAPPER.valueToTree(call.arguments()));
      node.put("result", call.result().orElse(null));
      node.put("error", call.error().orElse(null));
    }
    return array;
  }

  private static ArrayNode modelCalls(List<ModelCall> calls) {
    var array = MAPPER.createArrayNode();
    for (var call : calls) {
      var node = array.addObject();
      node.put("model", call.model());
      node.put("provider", call.provider());
      node.set("finishReasons", MAPPER.valueToTree(call.finishReasons()));
      node.put("inputTokens", call.inputTokens());
      node.put("outputTokens", call.outputTokens());
      node.put("durationMs", call.duration().toMillis());
      node.put("inputMessages", call.inputMessages());
      node.put("outputMessages", call.outputMessages());
    }
    return array;
  }

  private static ArrayNode guardrails(List<GuardrailResult> guardrails) {
    var array = MAPPER.createArrayNode();
    for (var guardrail : guardrails) {
      var node = array.addObject();
      node.put("name", guardrail.name());
      node.put("category", guardrail.category());
      node.put("passed", guardrail.passed());
      node.put("explanation", guardrail.explanation());
    }
    return array;
  }

  private static ArrayNode evalResults(List<EvalResult> results) {
    var array = MAPPER.createArrayNode();
    for (var result : results) {
      var node = array.addObject();
      node.put("evaluator", result.evaluator());
      node.put("verdict", result.verdict().name());
      node.put("detail", result.detail());
    }
    return array;
  }
}
