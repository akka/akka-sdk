/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.annotation.DoNotInherit;
import akka.javasdk.testkit.eval.ExperimentRunner.EvalReport;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Cases bound to the agent under test, ready to run. Obtained from {@link ExperimentCases#agent}.
 *
 * <p>An experiment is immutable. Every setting returns a new experiment and leaves this one
 * unchanged, so call {@link #run} on the experiment the last setting returned.
 *
 * <p>Not for user extension.
 *
 * @param <V> the reply type of the agent's command handler
 */
@DoNotInherit
public interface Experiment<V> {

  /**
   * Sets the gate {@link #run} checks. Without a gate every case must pass.
   *
   * @param gate the gate condition to satisfy
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when {@code gate} is null
   */
  Experiment<V> gate(Gate gate);

  /**
   * Sets how the reply of an attempt is compared with the expected reply of its case. Without a
   * grader the runner compares them with {@link Object#equals}, which suits a mocked model or a
   * typed reply. The result of the comparison is the {@link ExperimentRunner#EXPECTED} result of
   * the attempt, next to the verdicts of the bound evaluators, and the gate and the report treat it
   * like an evaluator verdict.
   *
   * <p>The grader is not called when the agent call failed or returned no value. The {@link
   * ExperimentRunner#EXPECTED} result is then inconclusive.
   *
   * @param grader the grader to evaluate the expected reply against the actual reply
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when no case has an expected reply, or {@code grader} is null
   */
  Experiment<V> grade(Grader<V> grader);

  /**
   * Sets the name the report carries, see {@link EvalReport#name}. It names the report file, so it
   * must be usable as a file name on every platform: not blank, at most 200 characters, and without
   * {@code < > : " / \ | ? *} or control characters. Without a name the report is named after the
   * test method that called {@link #run}, for example {@code SupportAgentEvalTest.qualityGate}.
   *
   * @param name the report name
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when the name is not usable as a file name
   */
  Experiment<V> name(String name);

  /**
   * Sets the directory {@link #run} writes the report file to, see {@link EvalReport#reportFile}.
   * Without one the report goes to {@code target/eval-reports} under the working directory.
   *
   * @param directory the directory to write the report file to
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when {@code directory} is null
   */
  Experiment<V> reportDirectory(Path directory);

  /**
   * Disables writing a report file on {@link #run}.
   *
   * @return a new experiment with the setting applied
   */
  Experiment<V> withoutReportFile();

  /**
   * Sets how long an attempt waits for the ledger to hold its interaction and one evaluation per
   * bound evaluator, {@link ExperimentRunner#DEFAULT_EVALUATION_TIMEOUT} unless set. An evaluator
   * without an evaluation at the timeout is inconclusive on that attempt. Raise it for a judge that
   * calls a real model or for a durable evaluator with slow steps.
   *
   * @param timeout duration to wait, must be positive
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when {@code timeout} is null, zero, or negative
   */
  Experiment<V> evaluationTimeout(Duration timeout);

  /**
   * Runs every case this many times in total, each time in a fresh session. Once by default. The
   * gate, the rates and the spend count every attempt, so {@link Gate#allCasesShouldPass} passes
   * only when every case passed in every run. The report names the evaluators that passed in some
   * runs and failed in others of the same case, see {@link EvalReport#cases}.
   *
   * <p>A case that passes in every run is not shown to always pass: {@code times} runs cannot show
   * a failure rate below one in {@code times}. The stubs and the runtime are not reset between
   * runs, so a stub or an evaluator must not depend on state an attempt writes.
   *
   * @param times number of times to run each case, at least 1
   * @return a new experiment with the setting applied
   * @throws IllegalArgumentException when {@code times} is less than 1
   */
  Experiment<V> repeat(int times);

  /**
   * Runs all cases, checks the gate and writes the report file. Does not throw on a failed gate;
   * assert on the report.
   *
   * @return the evaluation report
   */
  EvalReport run();
}
