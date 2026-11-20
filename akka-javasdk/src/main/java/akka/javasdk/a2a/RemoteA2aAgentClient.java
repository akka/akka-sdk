/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.stream.javadsl.Source;
import java.util.concurrent.CompletionStage;

/**
 * Client for one remote A2A agent.
 *
 * <p>The client reads the Agent Card from the configured URL, caches it, and talks to the agent
 * over the JSON-RPC binding when the card lists it, and over the HTTP+JSON binding otherwise. A
 * failure reported by the remote agent is an {@link A2aRemoteErrorException}. Any other failure,
 * such as an unreachable agent or a timeout, is an {@link A2aClientException}.
 *
 * <p>Methods that block have an {@code Async} counterpart that returns a {@link CompletionStage}.
 *
 * <p>Not for user extension, instances provided by the SDK through {@link A2aClient}.
 */
@DoNotInherit
public interface RemoteA2aAgentClient {

  /**
   * Returns the remote agent's Agent Card. The card is cached and fetched again when it is older
   * than {@code card-refresh} of the agent's configuration.
   */
  A2aAgentCard card();

  /** Asynchronous variant of {@link #card()}. */
  CompletionStage<A2aAgentCard> cardAsync();

  /**
   * Starts a {@code SendMessage} request for a skill of the remote agent.
   *
   * <p>A2A does not define a field for the skill. The client sends it as {@code skillId} in the
   * request's metadata.
   *
   * @param skill the id of the skill, or {@code null} to leave the choice to the remote agent
   * @param message the message. A missing message id is generated and a missing role is {@link
   *     A2aRole#USER}.
   */
  SendRequest send(String skill, A2aMessage message);

  /**
   * Sends a {@code SendStreamingMessage} request and returns the server-sent events as a stream.
   * The request starts when the stream is materialized. To continue a conversation, set the context
   * id on the message.
   */
  Source<A2aStreamResponse, NotUsed> stream(String skill, A2aMessage message);

  /** Fetches a task with {@code GetTask}. */
  A2aTask getTask(String taskId);

  /** Asynchronous variant of {@link #getTask(String)}. */
  CompletionStage<A2aTask> getTaskAsync(String taskId);

  /** Cancels a task with {@code CancelTask} and returns the task as the remote agent reports it. */
  A2aTask cancel(String taskId);

  /** Asynchronous variant of {@link #cancel(String)}. */
  CompletionStage<A2aTask> cancelAsync(String taskId);

  /** A {@code SendMessage} request that is not yet sent. */
  interface SendRequest {

    /**
     * Continues the conversation with this context id, overriding the context id of the message.
     */
    SendRequest inContext(String contextId);

    /** Sends the request and blocks for the reply, which is a task or a message. */
    A2aSendMessageResponse invoke();

    /** Sends the request without blocking. */
    CompletionStage<A2aSendMessageResponse> invokeAsync();
  }
}
