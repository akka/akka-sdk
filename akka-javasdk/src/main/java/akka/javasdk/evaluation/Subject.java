/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * The subject of an evaluation — what is being evaluated.
 *
 * <p>Each variant resolves to different content: an {@link Interaction} resolves to that
 * interaction's content, carried directly on {@link EvaluationContext#interaction()}; a {@link
 * Flow} or {@link Session} resolves to a collection of interactions (and, for a flow, its activity)
 * read through a subject client; an {@link EvaluatedEvaluation} resolves to a previously recorded
 * evaluation; an {@link Experiment} resolves to a whole, terminated experiment.
 */
public sealed interface Subject {

  /**
   * One agent interaction, on its own or as part of a flow.
   *
   * @param interactionId the stable id of the interaction being evaluated
   * @param agentComponentId the component id of the agent that produced the interaction, where
   *     known
   * @param flowId the flow the interaction belongs to, absent if the interaction was produced
   *     directly by an agent outside any flow
   */
  record Interaction(
      String interactionId, Optional<String> agentComponentId, Optional<String> flowId)
      implements Subject {}

  /** A whole flow, across all of its agents. */
  record Flow(String flowId) implements Subject {}

  /** A whole session. */
  record Session(String sessionId) implements Subject {}

  /**
   * An evaluation that has already been recorded, so an evaluator's output can itself be evaluated.
   */
  record EvaluatedEvaluation(String evaluationId) implements Subject {}

  /** A whole experiment, evaluated once the experiment has terminated. */
  record Experiment(String experimentId) implements Subject {}
}
