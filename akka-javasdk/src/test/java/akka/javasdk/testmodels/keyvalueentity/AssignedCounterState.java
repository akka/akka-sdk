/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testmodels.keyvalueentity;

public class AssignedCounterState {
  public final String counterId;
  public final String assigneeId;

  public AssignedCounterState(String counterId, String assigneeId) {
    this.counterId = counterId;
    this.assigneeId = assigneeId;
  }

  public AssignedCounterState assignTo(String assigneeId) {
    return new AssignedCounterState(counterId, assigneeId);
  }
}
