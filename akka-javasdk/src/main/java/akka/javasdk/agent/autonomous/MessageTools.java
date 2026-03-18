/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.client.ComponentClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Messaging tools — available when the strategy has messaging configured.
 *
 * <p>Provides tools for sending messages to team members. Messages are delivered to the recipient's
 * inbox entity and read by the StrategyExecutor at the start of each iteration.
 */
public class MessageTools {

  private static final Logger log = LoggerFactory.getLogger(MessageTools.class);

  private final ComponentClient componentClient;
  private final String teamId;
  private final String senderAgentId;

  public MessageTools(ComponentClient componentClient, String teamId, String senderAgentId) {
    this.componentClient = componentClient;
    this.teamId = teamId;
    this.senderAgentId = senderAgentId;
  }

  @FunctionTool(
      description =
          "Send a message to a team member by their agent ID. Use this to coordinate with"
              + " teammates, share findings, or request input.")
  public String sendMessage(String recipientAgentId, String message) {
    try {
      // Block self-messaging
      if (senderAgentId.equals(recipientAgentId)) {
        return "Error: you cannot send a message to yourself. Send messages to other team members.";
      }

      // Validate recipient is a team member
      var teamState =
          componentClient.forEventSourcedEntity(teamId).method(TeamEntity::getState).invoke();

      var isMember =
          teamState.members().stream().anyMatch(m -> m.agentId().equals(recipientAgentId));

      if (!isMember) {
        return "Error: '"
            + recipientAgentId
            + "' is not a team member. Team members: "
            + teamState.members().stream().map(TeamState.TeamMember::agentId).toList();
      }

      componentClient
          .forEventSourcedEntity(recipientAgentId)
          .method(MessageInboxEntity::send)
          .invoke(new MessageInboxEntity.SendRequest(senderAgentId, message));

      log.info("Message sent from {} to {}", senderAgentId, recipientAgentId);
      return "Message sent to " + recipientAgentId;
    } catch (Exception e) {
      log.error("Failed to send message to {}", recipientAgentId, e);
      return "Error sending message: " + e.getMessage();
    }
  }
}
