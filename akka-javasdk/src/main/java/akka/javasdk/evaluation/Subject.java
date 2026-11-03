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
   * The id of the evaluated interaction.
   *
   * @throws IllegalStateException if the subject is not an interaction
   * @deprecated Switch over the subject and read {@link Interaction#interactionId()} instead.
   */
  @Deprecated(since = "3.8.0")
  default String interactionId() {
    throw new IllegalStateException("Subject [" + this + "] is not an interaction");
  }

  /**
   * The component id of the agent that produced the evaluated interaction.
   *
   * @throws IllegalStateException if the subject is not an interaction
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
   * One run of a workflow, for a verdict about the outcome of the whole run rather than about one
   * agent interaction. The interactions of the run that the evaluator judges are passed as the
   * trigger attributes of the {@link EvaluationContext} when the evaluation is started, or kept in
   * the workflow state and read through the workflow by its id. The evaluation can start before the
   * workflow step that starts it is persisted, so the evaluator reads only what earlier steps
   * persisted.
   *
   * @param workflowId the id of the workflow instance
   * @param workflowComponentId the component id of the workflow, since a workflow id is unique per
   *     workflow type only
   */
  record Workflow(String workflowId, String workflowComponentId) implements Subject {}
}
