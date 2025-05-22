/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Interface for managing conversation history between users and AI models, using
 * CoreMemory provides functionality to store, retrieve, and manage messages
 * exchanged during conversations in an agent system.
 */
public interface CoreMemory {

  /**
   * Adds a user message to the conversation history for the specified session.
   *
   * @param componentId The unique identifier for the component type that added the message
   * @param sessionId The unique identifier for the conversation session
   * @param message The content of the user message to add
   */
  void addUserMessage(String componentId, String sessionId, String message);

  /**
   * Adds an AI message to the conversation history for the specified session.
   *
   * @param sessionId The unique identifier for the conversation session
   * @param message The content of the AI message to add
   */
  void addAiMessage(String sessionId, String message);

  /**
   * Adds an interaction between a user and an AI model to the conversation history for the specified session.
   * @param sessionId The unique identifier for the conversation session
   * @param userMessage The content of the user message
   * @param aiMessage The content of the AI message
   */
  void addInteraction(String sessionId, String userMessage, String aiMessage);

  /**
   * Retrieves the complete conversation history for the specified session.
   *
   * @param sessionId The unique identifier for the conversation session
   * @return The complete conversation history containing all messages
   */
  ConversationHistory getFullHistory(String sessionId);
}
