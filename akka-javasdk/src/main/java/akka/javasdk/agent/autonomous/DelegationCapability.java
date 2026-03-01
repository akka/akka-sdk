/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import java.util.List;

/** Capability to delegate subtasks to other autonomous agents. */
public record DelegationCapability(
    String agentClassName,
    String agentComponentId,
    String description,
    List<AcceptedTaskInfo> acceptedTasks)
    implements Capability {

  /** Metadata about a task definition accepted by a delegation target. */
  public record AcceptedTaskInfo(
      String description, String resultTypeName, String instructionTemplate) {}
}
