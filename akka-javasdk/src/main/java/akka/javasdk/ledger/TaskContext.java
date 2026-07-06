/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

/**
 * Autonomous-agent task metadata. Present only for interactions produced by an agent running as
 * part of a flow; absent for request-based agent interactions.
 *
 * @param agentInstanceId the id of the agent instance that ran the task
 * @param taskId the id of the task
 * @param taskName the name of the task
 * @param taskDescription the description of the task
 * @param taskResultType the type of the task's result
 * @param iterationNumber the iteration of the flow this interaction belongs to
 * @param maxIterations the maximum number of iterations configured for the flow
 */
public record TaskContext(
    String agentInstanceId,
    String taskId,
    String taskName,
    String taskDescription,
    String taskResultType,
    int iterationNumber,
    int maxIterations) {}
