/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * A message that a client sent with {@link
 * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)}, and its status.
 *
 * @param messageId the id of the message
 * @param status the status of the message
 */
public record MessageState(String messageId, MessageStatus status) {}
