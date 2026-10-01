/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.annotation.DoNotInherit;
import akka.javasdk.testkit.eval.ExperimentRunner.EvalReport;

/**
 * Cases bound to the agent under test, ready to run. Obtained from {@link ExperimentCases#agent}.
 *
 * <p>Not for user extension.
 */
@DoNotInherit
public interface Experiment {

  /** The gate {@link #run} checks. Without a gate every case must pass. */
  Experiment gate(Gate gate);

  /** Runs all cases and checks the gate. Does not throw on a failed gate; assert on the report. */
  EvalReport run();
}
