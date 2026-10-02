/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.annotation.DoNotInherit;
import akka.javasdk.testkit.eval.ExperimentRunner.EvalReport;
import java.nio.file.Path;

/**
 * Cases bound to the agent under test, ready to run. Obtained from {@link ExperimentCases#agent}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface Experiment {

  /** The gate {@link #run} checks. Without a gate every case must pass. */
  Experiment gate(Gate gate);

  /**
   * The name the report carries, see {@link EvalReport#name}. Not blank. Without a name the report
   * is named after the test method that called {@link #run} and the time the run started, for
   * example {@code SupportAgentEvalTest.qualityGate-20261001-101530-123}.
   */
  Experiment name(String name);

  /**
   * The directory {@link #run} writes the report file to, see {@link EvalReport#reportFile}.
   * Without one the report goes to {@code target/eval-reports} under the working directory.
   */
  Experiment reportDirectory(Path directory);

  /** {@link #run} writes no report file. */
  Experiment withoutReportFile();

  /**
   * Runs all cases, checks the gate and writes the report file. Does not throw on a failed gate;
   * assert on the report.
   */
  EvalReport run();
}
