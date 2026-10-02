/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import akka.javasdk.testkit.eval.ExperimentRunner.Report;
import com.fasterxml.jackson.core.JsonProcessingException;

/** Turns a {@link Report} into its {@link ReportDocument} and renders the document as JSON. */
final class EvalReportJson {

  private EvalReportJson() {}

  static String render(ReportDocument document) {
    try {
      return JsonSupport.getObjectMapper()
          .writerWithDefaultPrettyPrinter()
          .writeValueAsString(document);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("could not render the eval report as JSON", e);
    }
  }

  static ReportDocument document(Report report) {
    var passed = (int) report.results().stream().filter(CaseResult::passed).count();
    var spend = report.spend();
    return new ReportDocument(
        ReportDocument.FORMAT,
        ReportDocument.FORMAT_VERSION,
        report.name(),
        report.startedAt(),
        report.finishedAt(),
        new ReportDocument.Gate(report.verdict().passed(), report.verdict().detail()),
        new ReportDocument.Summary(
            report.results().size(), passed, report.results().size() - passed, report.passRate()),
        report.rates().entrySet().stream()
            .map(
                e ->
                    new ReportDocument.EvaluatorCounts(
                        e.getKey(),
                        e.getValue().passed(),
                        e.getValue().failed(),
                        e.getValue().inconclusive()))
            .toList(),
        new ReportDocument.Spend(
            spend.casesWithEvidence(),
            spend.modelCalls(),
            spend.inputTokens(),
            spend.outputTokens(),
            spend.latencyMs()),
        report.results().stream().map(EvalReportJson::evalCase).toList());
  }

  private static ReportDocument.Case evalCase(CaseResult result) {
    return new ReportDocument.Case(
        result.caseId(),
        result.passed(),
        interaction(result.interaction()),
        result.evalResults().stream()
            .map(r -> new ReportDocument.Result(r.evaluator(), r.verdict(), r.detail()))
            .toList());
  }

  private static ReportDocument.Interaction interaction(Interaction interaction) {
    return new ReportDocument.Interaction(
        interaction.input(),
        interaction.userMessage(),
        interaction.reply(),
        interaction.finalModelText(),
        interaction.latency().toMillis(),
        interaction.inputTokens(),
        interaction.outputTokens(),
        interaction.blocked(),
        interaction.toolCalls().stream()
            .map(c -> new ReportDocument.ToolCall(c.name(), c.arguments(), c.result(), c.error()))
            .toList(),
        interaction.modelCalls().stream()
            .map(
                c ->
                    new ReportDocument.ModelCall(
                        c.model(),
                        c.provider(),
                        c.finishReasons(),
                        c.inputTokens(),
                        c.outputTokens(),
                        c.duration().toMillis(),
                        c.inputMessages(),
                        c.outputMessages()))
            .toList(),
        interaction.guardrails().stream()
            .map(
                g ->
                    new ReportDocument.Guardrail(
                        g.name(), g.category(), g.passed(), g.explanation()))
            .toList());
  }
}
