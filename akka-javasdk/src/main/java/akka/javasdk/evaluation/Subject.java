/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

/**
 * The subject of an evaluation — the interaction being evaluated.
 *
 * <p>A subject identifies a single interaction that was produced by an agent, either as part of a
 * flow ({@link FlowInteraction}) or directly ({@link AgentInteraction}). The interaction is
 * referenced by its stable {@code interactionId}; an {@link Evaluator} uses it to fetch the records
 * for that interaction (for example, the transcript) so they can be evaluated.
 */
public sealed interface Subject {

  /**
   * The component id of the agent that produced the interaction.
   *
   * @return the agent component id
   */
  String agentComponentId();

  /**
   * The stable id of the interaction being evaluated.
   *
   * @return the interaction id
   */
  String interactionId();

  /** An interaction produced by an agent running as part of a flow. */
  record FlowInteraction(String flowId, String agentComponentId, String interactionId)
      implements Subject {}

  /** An interaction produced directly by an agent. */
  record AgentInteraction(String agentComponentId, String interactionId) implements Subject {}
}
