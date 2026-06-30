/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * The subject of an evaluation — the interaction being evaluated.
 *
 * <p>A subject identifies a single interaction that was produced by an agent, either as part of a
 * flow ({@link FlowInteraction}) or directly ({@link AgentInteraction}). An {@link Evaluator} uses
 * the subject to fetch the records for that interaction (for example, the transcript) so they can
 * be evaluated.
 */
public sealed interface Subject permits Subject.FlowInteraction, Subject.AgentInteraction {

  /**
   * The component id of the agent that produced the interaction.
   *
   * @return the agent component id
   */
  String agentComponentId();

  /**
   * The id of the session the interaction belongs to.
   *
   * @return the session id
   */
  String sessionId();

  /**
   * The sequence number of the interaction within its session.
   *
   * @return the sequence number
   */
  long sequenceNr();

  /** An interaction produced by an agent running as part of a flow. */
  record FlowInteraction(
      String flowId,
      String agentComponentId,
      Optional<String> agentInstanceId,
      String sessionId,
      long sequenceNr)
      implements Subject {}

  /** An interaction produced directly by an agent. */
  record AgentInteraction(
      String agentComponentId, String sessionId, String writerId, long sequenceNr)
      implements Subject {}
}
