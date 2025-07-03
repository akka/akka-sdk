/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import java.util.List;

/**
 * Interface for managing contextual session history between users and AI models.
 * <p>
 * SessionMemory provides functionality to store, retrieve, and manage messages exchanged during
 * interactions in an agent system. It enables agents to maintain context across multiple
 * interactions within the same session.
 * <p>
 * <strong>Default Implementation:</strong>
 * The default implementation is backed by {@link SessionMemoryEntity}, a built-in Event Sourced Entity
 * that automatically stores contextual history. This provides durability and allows for session
 * memory to be shared between multiple agents using the same session id.
 * <p>
 * <strong>Custom Implementation:</strong>
 * You can provide a custom implementation using {@link MemoryProvider#custom(SessionMemory)} to
 * store session memory in external databases or services.
 * <p>
 * <strong>Memory Management:</strong>
 * Session memory can be configured to limit the amount of history retained, either by message count
 * or total size, to control token usage and performance.
 */
public interface SessionMemory {

  /**
   * Adds an interaction between a user and an AI model to the session history for the
   * specified session.
   *
   * @param sessionId The unique identifier for the contextual session
   * @param userMessage The content of the user message
   * @param messages All other messages generated during this interaction, typically AiMessage but also Tool Call
   *                 responses.
   */
  void addInteraction(String sessionId, SessionMessage.UserMessage userMessage,
                       List<SessionMessage> messages);
  /**
   * Retrieves the complete session history for the specified session. For very long sessions,
   * this might return a compacted version of the history.
   *
   * @param sessionId The unique identifier for the contextual session
   * @return The complete session history containing all messages
   */
  SessionHistory getHistory(String sessionId);
}
