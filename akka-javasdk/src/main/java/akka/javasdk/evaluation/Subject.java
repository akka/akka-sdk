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
   * The stable id of the interaction being evaluated.
   *
   * @return the interaction id
   */
  String interactionId();

  /**
   * The component id of the agent that produced the interaction.
   *
   * @return the agent component id
   */
  String agentComponentId();

  /**
   * One agent interaction, on its own or as part of a flow.
   *
   * @param interactionId the stable id of the interaction
   * @param agentComponentId the component id of the agent that produced the interaction
   * @param flowId the flow the interaction belongs to, empty when the agent ran outside a flow
   */
  record Interaction(String interactionId, String agentComponentId, Optional<String> flowId)
      implements Subject {}
}
