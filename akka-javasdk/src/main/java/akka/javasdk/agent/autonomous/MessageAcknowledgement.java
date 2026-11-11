/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent.autonomous;

/**
 * The reply of an autonomous agent to a message sent with {@link
 * akka.javasdk.client.AutonomousAgentClient#sendMessage(AgentMessage)}.
 *
 * @param messageId the id set on the message, or the id that the runtime generated, also for a
 *     rejected message
 * @param status where the message went, or why the agent rejected it
 * @param duplicate true when a message with this id was received before. {@code status} is then the
 *     status of that message.
 */
public record MessageAcknowledgement(String messageId, MessageStatus status, boolean duplicate) {}
