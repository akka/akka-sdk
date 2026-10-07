/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseResult;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseSummary;
import akka.javasdk.testkit.eval.ExperimentRunner.Outcome;
import akka.javasdk.testkit.eval.ExperimentRunner.Rate;
import akka.javasdk.testkit.eval.ExperimentRunner.Report;
import akka.javasdk.testkit.eval.ExperimentRunner.RequirementSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import java.util.Map;

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
    var cases = report.cases();
    var passedAttempts = (int) report.results().stream().filter(CaseResult::passed).count();
    var spend = report.spend();
    return new ReportDocument(
        ReportDocument.FORMAT,
        ReportDocument.FORMAT_VERSION,
        report.name(),
        report.startedAt(),
        report.finishedAt(),
        report.runs(),
        new ReportDocument.Gate(report.verdict().passed(), report.verdict().detail()),
        new ReportDocument.Summary(
            cases.size(),
            count(cases, Outcome.PASSED),
            count(cases, Outcome.FAILED),
            count(cases, Outcome.INCONSISTENT),
            count(cases, Outcome.INCONCLUSIVE),
            (int)
                cases.stream()
                    .flatMap(c -> c.requirements().stream())
                    .filter(r -> r.outcome() == Outcome.INCONSISTENT)
                    .count(),
            report.results().size(),
            passedAttempts,
            (int) report.results().stream().filter(CaseResult::failed).count(),
            (int) report.results().stream().filter(CaseResult::inconclusive).count(),
            report.passRate()),
        evaluatorCounts(Report.rates(report.results())),
        new ReportDocument.Spend(
            spend.attemptsWithEvidence(),
            spend.modelCalls(),
            spend.inputTokens(),
            spend.outputTokens(),
            spend.latencyMs()),
        cases.stream().map(EvalReportJson::evalCase).toList(),
        report.results().stream().map(EvalReportJson::attempt).toList());
  }

  private static int count(List<CaseSummary> cases, Outcome outcome) {
    return (int) cases.stream().filter(c -> c.outcome() == outcome).count();
  }

  private static List<ReportDocument.EvaluatorCounts> evaluatorCounts(Map<String, Rate> rates) {
    return rates.entrySet().stream()
        .map(
            e ->
                new ReportDocument.EvaluatorCounts(
                    e.getKey(),
                    e.getValue().passed(),
                    e.getValue().failed(),
                    e.getValue().inconclusive()))
        .toList();
  }

  private static ReportDocument.Case evalCase(CaseSummary summary) {
    return new ReportDocument.Case(
        summary.caseId(),
        summary.outcome(),
        summary.passedRuns(),
        summary.failedRuns(),
        summary.inconclusiveRuns(),
        summary.requirements().stream().map(EvalReportJson::requirement).toList());
  }

  private static ReportDocument.Requirement requirement(RequirementSummary requirement) {
    return new ReportDocument.Requirement(
        requirement.index(),
        requirement.evaluator(),
        requirement.outcome(),
        requirement.passedIn(),
        requirement.failedIn(),
        requirement.inconclusiveIn());
  }

  private static ReportDocument.Attempt attempt(CaseResult result) {
    return new ReportDocument.Attempt(
        result.caseId(),
        result.run(),
        result.verdict(),
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
