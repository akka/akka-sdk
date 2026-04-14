/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.TaskRule;

public class TestResultRule implements TaskRule<TestTasks.TestResult> {

  @Override
  public Result onComplete(TestTasks.TestResult result) {
    if (result.value() == null || result.value().isEmpty()) {
      return new Result.Rejected("value must not be empty");
    }
    if (result.score() < 10) {
      return new Result.Rejected("score must be >= 10, was " + result.score());
    }
    return new Result.Accepted();
  }
}
