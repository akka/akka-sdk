/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.workflowentities;

public record FailingCounterState(String counterId, int value, boolean finished) {
  public FailingCounterState asFinished() {
    return new FailingCounterState(counterId, value, true);
  }

  public FailingCounterState asFinished(int value) {
    return new FailingCounterState(counterId, value, true);
  }

  public FailingCounterState inc() {
    return new FailingCounterState(counterId, value + 1, finished);
  }
}
