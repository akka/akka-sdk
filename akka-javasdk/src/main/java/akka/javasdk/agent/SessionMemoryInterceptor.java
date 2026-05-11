/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

/**
 * Interceptor for write operations on {@link SessionMemory}.
 *
 * <p>An interceptor transforms session messages immediately before they are persisted to session
 * memory. Each method has an identity default that returns the message unchanged, so
 * implementations only need to override the variant(s) they want to transform.
 *
 * <p>Hooks are provided for each top-level {@link SessionMessage} variant: user messages (text and
 * multimodal), AI replies, and tool call responses. Tool requests are nested inside {@link
 * SessionMessage.AiMessage} and are not exposed as a dedicated hook; rewrite them by overriding
 * {@link #beforeWrite(String, SessionMessage.AiMessage)} and rebuilding the message with the
 * transformed list.
 *
 * <p>Read-side behavior (history limit, filters, read/write toggles) is configured through {@link
 * MemoryProvider} and is not exposed here.
 *
 * <p><b>Thread-safety:</b> an interceptor instance is shared across every session and concurrent
 * request that uses it. The SDK does not synchronize, copy, or pool it. Keep interceptors
 * stateless, or rely only on immutable / thread-safe state (for example, a precompiled {@link
 * java.util.regex.Pattern} or a final configuration object). Mutable fields on the interceptor will
 * be hit concurrently and must be avoided unless you guard the access yourself.
 *
 * <p>Attach an interceptor to a memory provider with {@code withInterceptor}:
 *
 * <pre>{@code
 * MemoryProvider.fromConfig().withInterceptor(new SessionMemoryInterceptor() {
 *   @Override
 *   public SessionMessage.UserMessage beforeWrite(
 *       String sessionId, SessionMessage.UserMessage userMessage) {
 *     return new SessionMessage.UserMessage(
 *         userMessage.timestamp(),
 *         redact(userMessage.text()),
 *         userMessage.componentId());
 *   }
 * });
 * }</pre>
 */
public interface SessionMemoryInterceptor {

  /**
   * Called immediately before a {@link SessionMessage.UserMessage} is persisted to session memory.
   * The returned message is what gets written. The default implementation returns the input
   * unchanged.
   *
   * @param sessionId The unique identifier for the contextual session
   * @param userMessage The user message about to be persisted
   * @return The (possibly transformed) message to persist; must not be {@code null}
   */
  default SessionMessage.UserMessage beforeWrite(
      String sessionId, SessionMessage.UserMessage userMessage) {
    return userMessage;
  }

  /**
   * Called immediately before a {@link SessionMessage.MultimodalUserMessage} is persisted to
   * session memory. The returned message is what gets written. The default implementation returns
   * the input unchanged.
   *
   * @param sessionId The unique identifier for the contextual session
   * @param userMessage The multimodal user message about to be persisted
   * @return The (possibly transformed) message to persist; must not be {@code null}
   */
  default SessionMessage.MultimodalUserMessage beforeWrite(
      String sessionId, SessionMessage.MultimodalUserMessage userMessage) {
    return userMessage;
  }

  /**
   * Called immediately before an {@link SessionMessage.AiMessage} is persisted to session memory.
   * The returned message is what gets written. The default implementation returns the input
   * unchanged.
   *
   * <p>Common uses include stripping {@link SessionMessage.AiMessage#thinking()} from persistence,
   * redacting content from {@code text}, or rewriting {@code toolCallRequests} arguments.
   *
   * @param sessionId The unique identifier for the contextual session
   * @param aiMessage The AI message about to be persisted
   * @return The (possibly transformed) message to persist; must not be {@code null}
   */
  default SessionMessage.AiMessage beforeWrite(
      String sessionId, SessionMessage.AiMessage aiMessage) {
    return aiMessage;
  }

  /**
   * Called immediately before a {@link SessionMessage.ToolCallResponse} is persisted to session
   * memory. The returned message is what gets written. The default implementation returns the input
   * unchanged.
   *
   * <p>Common uses include truncating large tool outputs (e.g., search results or file dumps) or
   * redacting sensitive content before it lands in long-term memory.
   *
   * @param sessionId The unique identifier for the contextual session
   * @param toolCallResponse The tool call response about to be persisted
   * @return The (possibly transformed) message to persist; must not be {@code null}
   */
  default SessionMessage.ToolCallResponse beforeWrite(
      String sessionId, SessionMessage.ToolCallResponse toolCallResponse) {
    return toolCallResponse;
  }
}
