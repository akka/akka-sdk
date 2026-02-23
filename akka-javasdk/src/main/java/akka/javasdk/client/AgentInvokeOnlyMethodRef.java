/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import java.util.concurrent.CompletionStage;

/**
 * Zero argument agent component call representation, returning a detailed reply including token
 * usage, not executed until invoked.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface AgentInvokeOnlyMethodRef<R> {

  /**
   * Execute the call and block until the response is available.
   *
   * @return The agent reply including the result value
   */
  R invoke();

  /**
   * Execute the call asynchronously.
   *
   * @return A CompletionStage that completes with the agent reply including the result value
   */
  CompletionStage<R> invokeAsync();

  /**
   * Switch to a detailed reply mode that includes e.g. token usage information.
   *
   * @return A call representation that returns {@link akka.javasdk.agent.Agent.AgentReply}
   */
  AgentReplyInvokeOnlyMethodRef<R> withDetailedReply();
}
