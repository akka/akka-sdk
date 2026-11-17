/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * The subject of an evaluation. A subject names what is evaluated by its stable id. The evaluator
 * fetches the content from the ledger.
 */
public sealed interface Subject {

  /**
   * The ID of the evaluated interaction.
   *
   * @return the interaction ID
   * @throws IllegalStateException if this subject is not an interaction
   * @deprecated Switch over the subject and read {@link Interaction#interactionId()} instead.
   */
  @Deprecated(since = "3.8.0")
  default String interactionId() {
    throw new IllegalStateException("Subject [" + this + "] is not an interaction");
  }

  /**
   * The component ID of the agent that produced the evaluated interaction.
   *
   * @return the agent component ID
   * @throws IllegalStateException if this subject is not an interaction
   * @deprecated Switch over the subject and read {@link Interaction#agentComponentId()} instead.
   */
  @Deprecated(since = "3.8.0")
  default String agentComponentId() {
    throw new IllegalStateException("Subject [" + this + "] is not an interaction");
  }

  /**
   * One agent interaction, on its own or as part of a flow.
   *
   * @param interactionId the stable id of the interaction
   * @param agentComponentId the component id of the agent that produced the interaction
   * @param flowId the flow the interaction belongs to, empty when the agent ran outside a flow
   */
  record Interaction(String interactionId, String agentComponentId, Optional<String> flowId)
      implements Subject {}

  /**
   * One run of a workflow, evaluated as a whole rather than as an individual agent interaction.
   *
   * <p>Interactions to evaluate can be passed as trigger attributes when starting the evaluation,
   * or read from workflow state. The evaluation can start before the workflow step that starts it
   * is persisted, so the evaluator reads only what earlier steps persisted.
   *
   * @param workflowId the ID of the workflow instance
   * @param workflowComponentId the component ID of the workflow, since a workflow ID is unique per
   *     workflow type only
   */
  record Workflow(String workflowId, String workflowComponentId) implements Subject {}
}
