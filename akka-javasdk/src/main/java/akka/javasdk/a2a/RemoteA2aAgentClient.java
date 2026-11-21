/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.client.ComponentDeferredMethodRef1;
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
 * <p>Each method returns a request that is not yet sent. Send it with {@code invoke()}, which
 * blocks for the reply, or with {@code invokeAsync()}, as with the requests of {@link
 * akka.javasdk.client.ComponentClient}:
 *
 * <pre>{@code
 * A2aTask task = client.getTask(taskId).invoke();
 * }</pre>
 *
 * <p>Not for user extension, instances provided by the SDK through {@link A2aClient}.
 */
@DoNotInherit
public interface RemoteA2aAgentClient {

  /**
   * The remote agent's Agent Card. The card is cached and fetched again when it is older than
   * {@code card-refresh} of the agent's configuration.
   */
  Request<A2aAgentCard> card();

  /**
   * A {@code SendMessage} request that leaves the choice of skill to the remote agent.
   *
   * @param message the message. A missing message id is generated and a missing role is {@link
   *     A2aRole#USER}.
   */
  SendRequest send(A2aMessage message);

  /**
   * A {@code SendMessage} request for a skill of the remote agent.
   *
   * <p>A2A does not define a field for the skill. The client sends it as {@code skillId} in the
   * request's metadata.
   *
   * @param skill the id of the skill
   * @param message the message. A missing message id is generated and a missing role is {@link
   *     A2aRole#USER}.
   */
  SendRequest send(String skill, A2aMessage message);

  /**
   * Sends a {@code SendStreamingMessage} request and returns the server-sent events as a stream,
   * leaving the choice of skill to the remote agent. The request starts when the stream is
   * materialized. To continue a conversation, set the context id on the message.
   */
  Source<A2aStreamResponse, NotUsed> stream(A2aMessage message);

  /**
   * Sends a {@code SendStreamingMessage} request for a skill and returns the server-sent events as
   * a stream. The request starts when the stream is materialized. To continue a conversation, set
   * the context id on the message.
   */
  Source<A2aStreamResponse, NotUsed> stream(String skill, A2aMessage message);

  /** A {@code GetTask} request. */
  Request<A2aTask> getTask(String taskId);

  /** A {@code CancelTask} request. Its reply is the task as the remote agent reports it. */
  Request<A2aTask> cancel(String taskId);

  /**
   * A request to the remote agent that is not yet sent.
   *
   * @param <R> the type of the reply
   */
  @DoNotInherit
  interface Request<R> {
    /** Sends the request and blocks for the reply. */
    R invoke();

    /** Sends the request without blocking. */
    CompletionStage<R> invokeAsync();
  }

  /** A {@code SendMessage} request that is not yet sent. Its reply is a task or a message. */
  @DoNotInherit
  interface SendRequest extends Request<A2aSendMessageResponse> {
    /**
     * Continues the conversation with this context id, overriding the context id of the message.
     */
    SendRequest inContext(String contextId);

    /**
     * Delivers the later updates of the task that this message starts, or continues, to a command
     * of a workflow.
     *
     * <p>Pass a method reference of the component client, which names the workflow by its id:
     *
     * <pre>{@code
     * a2aClient
     *     .forAgent("claims")
     *     .send("assess-claim", message)
     *     .notifyWorkflow(componentClient.forWorkflow(workflowId).method(ClaimFlow::onUpdate))
     *     .invoke();
     * }</pre>
     *
     * <p>The message is sent with {@code returnImmediately}, so the reply carries the task as the
     * remote agent accepted it. The reply itself is not delivered to the command. When the task in
     * the reply has not ended, each later status change and each new artifact is delivered to the
     * command until the task ends:
     *
     * <ul>
     *   <li>When the Agent Card of the remote agent says that it sends push notifications and
     *       {@code akka.javasdk.a2a.push-receiver.public-url} (or {@code
     *       akka.javasdk.a2a.endpoint.public-url}) is set, the client registers a push notification
     *       configuration on the task. Its URL points at an endpoint of this service, and its token
     *       is bound to the workflow and the task.
     *   <li>Otherwise, a workflow of the SDK asks for the task with {@code GetTask} every {@code
     *       poll-interval} of the remote agent's configuration, until the task ends or needs input.
     *       The polling continues across restarts of the service.
     * </ul>
     *
     * <p>Delivery is at least once. An update that was already delivered for this workflow and
     * task, identified by its state and timestamp or by the artifact's content, is not delivered
     * again. A reply that is a message, or a task that has ended, starts no delivery.
     *
     * <p>When the task has not ended or asked for input after the {@code follow-timeout} of the
     * remote agent's configuration, or when polling fails 20 times in a row, the following ends:
     * the command receives a last {@link A2aTaskStatusUpdate} whose {@link
     * A2aTaskStatusUpdate#followTimedOut()} is true, and no later update.
     *
     * @param workflowCommand a component client method reference to a workflow command that takes
     *     an {@link A2aTaskUpdate}
     * @throws IllegalArgumentException if the method reference is not one of a workflow
     */
    SendRequest notifyWorkflow(ComponentDeferredMethodRef1<A2aTaskUpdate, ?> workflowCommand);
  }
}
