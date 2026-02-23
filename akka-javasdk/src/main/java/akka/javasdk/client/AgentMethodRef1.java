/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.Metadata;
import akka.pattern.RetrySettings;
import java.util.concurrent.CompletionStage;

/**
 * One argument agent component call representation.
 *
 * <p>Extends {@link ComponentMethodRef1} with the ability to get a detailed reply including token
 * usage through {@link #withDetailedReply()}.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <A1> The argument type of the call
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface AgentMethodRef1<A1, R> extends ComponentDeferredMethodRef1<A1, R> {

  @Override
  AgentMethodRef1<A1, R> withMetadata(Metadata metadata);

  /**
   * Set the retry settings for this call.
   *
   * @param retrySettings The retry settings
   * @return A new call with the retry settings set
   */
  AgentInvokeOnlyMethodRef1<A1, R> withRetry(RetrySettings retrySettings);

  /**
   * Set the retry settings for this call. A predefined backoff strategy will be calculated based on
   * the number of maxRetries.
   *
   * @param maxRetries The number of retries to make
   * @return A new call with the retry settings set
   */
  AgentInvokeOnlyMethodRef1<A1, R> withRetry(int maxRetries);

  /**
   * Switch to a detailed reply mode that includes e.g. token usage information.
   *
   * @return A call representation that returns {@link akka.javasdk.agent.Agent.AgentReply}
   */
  AgentReplyInvokeOnlyMethodRef1<A1, R> withDetailedReply();

  CompletionStage<R> invokeAsync(A1 arg);

  R invoke(A1 arg);
}
