/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Interface for managing session history between users and AI models, using SessionMemory
 * provides functionality to store, retrieve, and manage messages exchanged during conversations in
 * an agent system.
 */
public interface SessionMemory {

  /**
   * Adds an interaction between a user and an AI model to the session history for the
   * specified session.
   *
   * @param sessionId The unique identifier for the conversation session
   * @param userMessage The content of the user message
   * @param aiMessage The content of the AI message
   */
  void addInteraction(String sessionId, SessionMessage.UserMessage userMessage, SessionMessage.AiMessage aiMessage);

  /**
   * Retrieves the complete session history for the specified session. For very long sessions,
   * this might return a compacted version of the history.
   *
   * @param sessionId The unique identifier for the conversation session
   * @return The complete session history containing all messages
   */
  SessionHistory getHistory(String sessionId);
}
