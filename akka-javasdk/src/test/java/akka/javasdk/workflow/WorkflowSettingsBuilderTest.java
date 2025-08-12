/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class WorkflowSettingsBuilderTest {

  public static class TestWorkflow extends Workflow<TestWorkflow.State> {
    record State(String value) {}

    @Override
    public WorkflowSettings settings() {

      return WorkflowSettings.builder()
          .stepTimeout(TestWorkflow::stepWithTimeoutAndRecovery, Duration.ofSeconds(3))
          .stepRecovery(
              TestWorkflow::stepWithTimeoutAndRecovery,
              maxRetries(2).failoverTo(TestWorkflow::interruptStep))
          // redefine settings for stepWithTimeoutAndRecovery
          .stepTimeout(TestWorkflow::stepWithTimeoutAndRecovery, Duration.ofSeconds(4))
          .stepRecovery(
              TestWorkflow::stepWithTimeoutAndRecovery,
              maxRetries(1).failoverTo(TestWorkflow::interruptStep))
          .stepTimeout(TestWorkflow::stepWithTimeout, Duration.ofSeconds(3))
          .stepRecovery(
              TestWorkflow::stepWithRecovery, maxRetries(1).failoverTo(TestWorkflow::interruptStep))
          .build();
    }

    private StepEffect stepWithTimeoutAndRecovery() {
      return stepEffects().thenEnd();
    }

    private StepEffect stepWithTimeout() {
      return stepEffects().thenEnd();
    }

    private StepEffect stepWithRecovery() {
      return stepEffects().thenEnd();
    }

    private StepEffect interruptStep() {
      return stepEffects().thenEnd();
    }
  }

  @Test
  public void stepTimeoutAndRecoveryDoNotRestEachOther() {
    var wf = new TestWorkflow();

    var stepSettings =
        wf.settings().stepSettings().stream()
            .filter(s -> s.stepName().equals("stepWithTimeoutAndRecovery"))
            .findFirst()
            .get();

    assertTrue(stepSettings.timeout().isPresent());
    // the last set timeout is 4
    assertEquals(stepSettings.timeout().get(), Duration.ofSeconds(4));

    assertTrue(stepSettings.recovery().isPresent());
    // the last set max-retry is 1
    assertEquals(stepSettings.recovery().get().maxRetries(), 1);
  }

  @Test
  public void stepWithOnlyTimeoutHasNoRecovery() {
    var wf = new TestWorkflow();
    var stepSettings =
        wf.settings().stepSettings().stream()
            .filter(s -> s.stepName().equals("stepWithTimeout"))
            .findFirst()
            .get();
    assertTrue(stepSettings.timeout().isPresent());
    assertFalse(stepSettings.recovery().isPresent());
  }

  @Test
  public void stepWithOnlyRecoveryHasNoTimeout() {
    var wf = new TestWorkflow();
    var stepSettings =
        wf.settings().stepSettings().stream()
            .filter(s -> s.stepName().equals("stepWithRecovery"))
            .findFirst()
            .get();
    assertTrue(stepSettings.recovery().isPresent());
    assertFalse(stepSettings.timeout().isPresent());
  }
}
