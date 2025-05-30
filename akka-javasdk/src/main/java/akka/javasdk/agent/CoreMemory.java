/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Interface for managing conversation history between users and AI models, using CoreMemory
 * provides functionality to store, retrieve, and manage messages exchanged during conversations in
 * an agent system.
 */
public interface CoreMemory {

  /**
   * Adds an interaction between a user and an AI model to the conversation history for the
   * specified session.
   *
   * @param sessionId The unique identifier for the conversation session
   * @param componentId The unique identifier for the component (i.e., agent) involved in the
   *     interaction
   * @param userMessage The content of the user message
   * @param aiMessage The content of the AI message
   */
  void addInteraction(String sessionId, String componentId, ConversationMessage.UserMessage userMessage, ConversationMessage.AiMessage aiMessage);

  /**
   * Retrieves the complete conversation history for the specified session. For very long sessions,
   * this might return a compacted version of the history.
   *
   * @param sessionId The unique identifier for the conversation session
   * @return The complete conversation history containing all messages
   */
  ConversationHistory getHistory(String sessionId);
}
