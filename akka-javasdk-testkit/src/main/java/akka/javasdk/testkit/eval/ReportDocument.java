/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.eval.ExperimentRunner.Outcome;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * The report file as data: what {@link Experiment#run} writes to {@code
 * target/eval-reports/<name>-<start time>.json}, and what {@link #read} gives back. The file is
 * this record as JSON, in the {@code akka-eval-report} format described by {@code
 * akka/javasdk/testkit/eval/eval-report.schema.json} on the testkit classpath. A reader ignores
 * properties it does not know.
 *
 * <p>An attempt is one case in one run. Without {@link Experiment#repeat} every case has one
 * attempt.
 *
 * @param format always {@value #FORMAT}
 * @param formatVersion {@value #FORMAT_VERSION}; increments when a property changes meaning or is
 *     removed
 * @param name the name given with {@link Experiment#name}, or the test method that ran the
 *     experiment
 * @param startedAt when the run started
 * @param finishedAt when the last attempt was evaluated
 * @param runs how many times every case ran
 * @param gate the gate verdict
 * @param summary the case and attempt counts and the pass rate
 * @param evaluators one entry per evaluator, in the order the evaluators first appear in the
 *     attempts, counting every attempt
 * @param guardrails one entry per guardrail, in the order the guardrails first appear in the
 *     attempts, counting every decision
 * @param spend model responses, tokens and latency summed over the attempts with a recorded
 *     interaction
 * @param cases one entry per case, in the order the cases were given, with its outcome over all
 *     runs and the verdicts of its evaluators
 * @param attempts one entry per attempt: every case in the order the cases were given, its runs
 *     ascending
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
    List<GuardrailCounts> guardrails,
    Spend spend,
    List<Case> cases,
    List<Attempt> attempts) {

  public static final String FORMAT = "akka-eval-report";
  public static final int FORMAT_VERSION = 4;

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
   * @param passedCases cases with the outcome PASSED
   * @param failedCases cases with the outcome FAILED
   * @param inconsistentCases cases with the outcome INCONSISTENT; zero with one run
   * @param inconclusiveCases cases with the outcome INCONCLUSIVE
   * @param inconsistentEvaluators evaluators that passed in some runs and failed in others of the
   *     same case, over all cases; zero with one run
   * @param attempts {@code cases} times {@code runs}
   * @param passedAttempts attempts with the verdict PASS
   * @param failedAttempts attempts with the verdict FAIL
   * @param inconclusiveAttempts attempts with the verdict INCONCLUSIVE
   * @param passRate {@code passedAttempts} over {@code attempts}
   */
  public record Summary(
      int cases,
      int passedCases,
      int failedCases,
      int inconsistentCases,
      int inconclusiveCases,
      int inconsistentEvaluators,
      int attempts,
      int passedAttempts,
      int failedAttempts,
      int inconclusiveAttempts,
      double passRate) {}

  /**
   * @param evaluator the evaluator component id, or {@link ExperimentRunner#TARGET} for the result
   *     the runner reports when the agent call failed
   * @param passed results with the verdict PASS
   * @param failed results with the verdict FAIL
   * @param inconclusive results with the verdict INCONCLUSIVE
   * @param notApplicable results with the verdict NOT_APPLICABLE
   */
  public record EvaluatorCounts(
      String evaluator, int passed, int failed, int inconclusive, int notApplicable) {}

  /**
   * @param guardrail the configured name of the guardrail
   * @param allowed decisions that allowed the content
   * @param denied decisions that denied the content
   * @param failed guardrail runs that threw instead of deciding
   */
  public record GuardrailCounts(String guardrail, int allowed, int denied, int failed) {}

  /**
   * All zero when no attempt has a recorded interaction.
   *
   * @param attemptsWithEvidence attempts with a recorded interaction
   * @param modelResponses summed over those attempts
   * @param inputTokens summed over those attempts
   * @param outputTokens summed over those attempts
   * @param latencyMs summed over those attempts
   */
  public record Spend(
      int attemptsWithEvidence,
      int modelResponses,
      long inputTokens,
      long outputTokens,
      long latencyMs) {}

  /**
   * One case over all its runs.
   *
   * @param id the case id
   * @param outcome over all runs: PASSED when every run passed, FAILED when every run failed,
   *     INCONCLUSIVE when every run was inconclusive, INCONSISTENT otherwise
   * @param passedRuns runs with the verdict PASS
   * @param failedRuns runs with the verdict FAIL
   * @param inconclusiveRuns runs with the verdict INCONCLUSIVE
   * @param verdicts the verdicts of each evaluator of the case, in the order the results first
   *     appear in its attempts; the runner's target result counts as an evaluator
   */
  public record Case(
      String id,
      Outcome outcome,
      int passedRuns,
      int failedRuns,
      int inconclusiveRuns,
      List<EvaluatorVerdicts> verdicts) {}

  /**
   * The verdicts of one evaluator over all runs of a case.
   *
   * @param evaluator the evaluator component id, or {@link ExperimentRunner#TARGET}
   * @param outcome over the runs where the evaluator reached a verdict: PASSED, FAILED,
   *     INCONSISTENT when it passed in some runs and failed in others, or INCONCLUSIVE when it was
   *     conclusive in no run
   * @param passedIn the runs with the verdict PASS, ascending
   * @param failedIn the runs with the verdict FAIL, ascending
   * @param inconclusiveIn the runs with the verdict INCONCLUSIVE, ascending
   * @param notApplicableIn the runs with the verdict NOT_APPLICABLE, ascending
   */
  public record EvaluatorVerdicts(
      String evaluator,
      Outcome outcome,
      List<Integer> passedIn,
      List<Integer> failedIn,
      List<Integer> inconclusiveIn,
      List<Integer> notApplicableIn) {}

  /**
   * One case in one run.
   *
   * @param id the case id
   * @param run the run, from 1
   * @param verdict FAIL when a result failed, INCONCLUSIVE when no result failed and a result is
   *     inconclusive or no result passed, PASS otherwise
   * @param evidence what the attempt left in the ledger
   * @param results one entry per evaluator
   */
  public record Attempt(
      String id, int run, EvalResult.Verdict verdict, Evidence evidence, List<Result> results) {}

  /**
   * @param input the command sent to the agent; a String command as is, any other command as JSON
   * @param interactionId the id of the recorded interaction; empty when none was recorded
   * @param systemMessage the system message of the interaction; empty when none was recorded
   * @param userMessage the input message as the model saw it, as text; empty when none was recorded
   * @param reply the agent's reply as text; empty when none was recorded
   * @param returned the value the command handler returned, as text; empty when the call failed
   * @param failure why the interaction failed; empty when it did not fail
   * @param model the model the interaction was recorded with
   * @param finishReason why the last model call finished, as recorded; empty when none was recorded
   * @param latencyMs from the start of the model call to its end
   * @param inputTokens summed over the model calls
   * @param outputTokens summed over the model calls
   * @param modelResponses how many model responses the interaction holds
   * @param responses the model responses, in order
   * @param denied whether a guardrail denied content in the interaction
   * @param toolCalls in call order
   * @param guardrails the guardrail decisions made in the interaction, newest first
   */
  public record Evidence(
      String input,
      String interactionId,
      String systemMessage,
      String userMessage,
      String reply,
      String returned,
      String failure,
      Model model,
      String finishReason,
      long latencyMs,
      long inputTokens,
      long outputTokens,
      int modelResponses,
      List<Response> responses,
      boolean denied,
      List<ToolCall> toolCalls,
      List<Guardrail> guardrails) {}

  /**
   * @param provider the model provider, as recorded; empty when none was recorded
   * @param name the model name, as recorded; empty when none was recorded
   */
  public record Model(String provider, String name) {}

  /**
   * @param content the text of the response; empty when the response holds only tool calls
   * @param inputTokens the input tokens of the model call
   * @param outputTokens the output tokens of the model call
   * @param toolCalls the names of the tools the response requested, in order
   */
  public record Response(
      String content, long inputTokens, long outputTokens, List<String> toolCalls) {}

  /**
   * @param name the registered tool name, {@code <Class>_<method>} for a function tool method
   *     without a name of its own
   * @param arguments the arguments as the JSON the model sent
   * @param response the tool's result as recorded; empty when none was recorded
   */
  public record ToolCall(String name, String arguments, String response) {}

  /**
   * @param name the configured name of the guardrail
   * @param category the configured category
   * @param boundary where in the interaction the guardrail ran
   * @param decision what the guardrail decided
   * @param reportOnly whether the guardrail only reports, so the interaction continued regardless
   * @param explanation the explanation the guardrail gave; empty when it gave none
   */
  public record Guardrail(
      String name,
      String category,
      String boundary,
      String decision,
      boolean reportOnly,
      String explanation) {}

  /**
   * @param evaluator the evaluator component id, or {@link ExperimentRunner#TARGET}
   * @param verdict PASS, FAIL, INCONCLUSIVE or NOT_APPLICABLE
   * @param detail the explanation and the score, or why there is no verdict; empty when the
   *     evaluator gave none
   */
  public record Result(String evaluator, EvalResult.Verdict verdict, String detail) {}
}
