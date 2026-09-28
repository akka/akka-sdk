/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.evaluation;

import java.util.Optional;

/**
 * What an evaluation evaluates. A subject names the thing under evaluation by its stable id; the
 * content is fetched from the ledger.
 */
public sealed interface Subject {

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
