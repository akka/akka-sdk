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
   * The name the report carries, see {@link EvalReport#name}. Not blank and without a path
   * separator, as it names the report file. Without a name the report is named after the test
   * method that called {@link #run}, for example {@code SupportAgentEvalTest.qualityGate}.
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
   * Runs every case this many times in total, each time in a fresh session. Once by default. The
   * gate, the rates and the spend count every turn, so {@link Gate#allCasesShouldPass} passes only
   * when every case passed in every run. The report names the cases that passed in some runs and
   * failed in others, see {@link EvalReport#cases}.
   *
   * @param times at least 1
   */
  Experiment repeat(int times);

  /**
   * Runs all cases, checks the gate and writes the report file. Does not throw on a failed gate;
   * assert on the report.
   */
  EvalReport run();
}
