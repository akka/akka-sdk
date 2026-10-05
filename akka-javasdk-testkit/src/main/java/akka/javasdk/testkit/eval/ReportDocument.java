/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.eval.ExperimentRunner.CaseSummary;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The report file as data: what {@link Experiment#run} writes to {@code
 * target/eval-reports/<name>-<start time>.json}, and what {@link #read} gives back. The file is
 * this record as JSON, in the {@code akka-eval-report} format described by {@code
 * akka/javasdk/testkit/eval/eval-report.schema.json} on the testkit classpath. A reader ignores
 * properties it does not know.
 *
 * <p>A turn is one case in one run. Without {@link Experiment#runs} every case has one turn.
 *
 * @param format always {@value #FORMAT}
 * @param formatVersion {@value #FORMAT_VERSION}; increments when a property changes meaning or is
 *     removed
 * @param name the name given with {@link Experiment#name}, or the test method that ran the
 *     experiment
 * @param startedAt when the run started
 * @param finishedAt when the last turn was evaluated
 * @param runs how many times every case ran
 * @param gate the gate verdict
 * @param summary the case and turn counts and the pass rate
 * @param evaluators one entry per evaluator label, in the order the labels first appear in the
 *     turns, counting every turn
 * @param spend model calls, tokens and latency summed over the turns whose evidence carries model
 *     calls
 * @param cases one entry per case, in the order the cases were given, with its outcome over all
 *     runs
 * @param turns one entry per turn: every case in the order the cases were given, its runs ascending
 */
public record ReportDocument(
    String format,
    int formatVersion,
    String name,
    Instant startedAt,
    Instant finishedAt,
    int runs,
    Gate gate,
    Summary summary,
    List<EvaluatorCounts> evaluators,
    Spend spend,
    List<Case> cases,
    List<Turn> turns) {

  public static final String FORMAT = "akka-eval-report";
  public static final int FORMAT_VERSION = 1;

  /** Reads a report file written by {@link Experiment#run}. */
  public static ReportDocument read(Path file) {
    if (file == null) throw new IllegalArgumentException("file required");
    try {
      return JsonSupport.getObjectMapper().readValue(file.toFile(), ReportDocument.class);
    } catch (IOException e) {
      throw new UncheckedIOException("could not read the eval report " + file, e);
    }
  }

  /**
   * @param passed whether the gate passed
   * @param detail the verdict as text; a combined gate joins the detail of each part with a
   *     semicolon
   */
  public record Gate(boolean passed, String detail) {}

  /**
   * @param cases the number of cases
   * @param passedCases cases with no failed result in any run
   * @param failedCases cases with a failed result in every run
   * @param inconsistentCases cases that passed in some runs and failed in others; zero with one run
   * @param turns {@code cases} times {@code runs}
   * @param passedTurns turns with no failed result
   * @param failedTurns turns with a failed result
   * @param passRate {@code passedTurns} over {@code turns}
   */
  public record Summary(
      int cases,
      int passedCases,
      int failedCases,
      int inconsistentCases,
      int turns,
      int passedTurns,
      int failedTurns,
      double passRate) {}

  /**
   * @param evaluator the evaluator label; a custom evaluator carries the {@link
   *     Evaluators#CUSTOM_PREFIX}, and {@link Evaluators#TARGET} and {@link Evaluators#SETUP} are
   *     reported by the runner when a turn did not reach evaluation
   * @param passed results with the verdict PASS
   * @param failed results with the verdict FAIL
   * @param inconclusive results with the verdict INCONCLUSIVE
   */
  public record EvaluatorCounts(String evaluator, int passed, int failed, int inconclusive) {}

  /**
   * All zero when no turn carries model calls.
   *
   * @param turnsWithEvidence turns whose evidence carries model calls
   * @param modelCalls summed over those turns
   * @param inputTokens summed over those turns
   * @param outputTokens summed over those turns
   * @param latencyMs summed over those turns
   */
  public record Spend(
      int turnsWithEvidence, int modelCalls, long inputTokens, long outputTokens, long latencyMs) {}

  /**
   * One case over all its runs.
   *
   * @param id the case id
   * @param outcome whether the case passed in every run, failed in every run, or both
   * @param passedRuns runs with no failed result
   * @param failedRuns runs with a failed result
   * @param evaluators one entry per evaluator label that reported on the case, in the order the
   *     labels first appear in its turns, counting every run
   */
  public record Case(
      String id,
      CaseSummary.Outcome outcome,
      int passedRuns,
      int failedRuns,
      List<EvaluatorCounts> evaluators) {}

  /**
   * One case in one run.
   *
   * @param id the case id
   * @param run the run, from 1
   * @param passed no result has the verdict FAIL
   * @param interaction the input, the reply and the traced evidence
   * @param results one entry per evaluator
   */
  public record Turn(
      String id, int run, boolean passed, Interaction interaction, List<Result> results) {}

  /**
   * @param input the command sent to the agent; a String command as is, any other command as JSON
   * @param userMessage the user message the agent sent the model; empty when the trace did not
   *     carry it
   * @param reply the agent's reply as text; empty when the agent call failed
   * @param finalModelText the text of the last model response, before the agent mapped it into the
   *     reply; empty when the trace did not carry it
   * @param latencyMs from the start of the agent command to its end
   * @param inputTokens summed over the model calls
   * @param outputTokens summed over the model calls
   * @param blocked whether a guardrail blocked the turn
   * @param toolCalls in call order
   * @param modelCalls in call order
   * @param guardrails every guardrail evaluation, in order
   */
  public record Interaction(
      String input,
      String userMessage,
      String reply,
      String finalModelText,
      long latencyMs,
      long inputTokens,
      long outputTokens,
      boolean blocked,
      List<ToolCall> toolCalls,
      List<ModelCall> modelCalls,
      List<Guardrail> guardrails) {}

  /**
   * @param name the plain tool name, without the agent class prefix
   * @param arguments by parameter name; a value is null when the model sent null
   * @param result the tool's result as the model saw it, when recorded
   * @param error the failure message when the tool threw
   */
  public record ToolCall(
      String name,
      Map<String, Object> arguments,
      Optional<String> result,
      Optional<String> error) {}

  /**
   * @param model the model name, as sent in the request
   * @param provider the provider name
   * @param finishReasons why the model stopped, as the provider reports it
   * @param inputTokens tokens sent; zero when the provider reports none
   * @param outputTokens tokens received; zero when the provider reports none
   * @param durationMs the duration of the call
   * @param inputMessages the messages sent, as the runtime renders them; empty when not recorded
   * @param outputMessages the messages received; empty when not recorded
   */
  public record ModelCall(
      String model,
      String provider,
      List<String> finishReasons,
      long inputTokens,
      long outputTokens,
      long durationMs,
      String inputMessages,
      String outputMessages) {}

  /**
   * @param name the configured name of the guardrail
   * @param category the configured category
   * @param passed whether the guardrail let the content through
   * @param explanation why it blocked; empty when it passed
   */
  public record Guardrail(String name, String category, boolean passed, String explanation) {}

  /**
   * @param evaluator the evaluator label
   * @param verdict PASS, FAIL or INCONCLUSIVE
   * @param detail the reason, or the score; empty when the evaluator gave none
   */
  public record Result(String evaluator, Evaluator.EvalResult.Verdict verdict, String detail) {}
}
