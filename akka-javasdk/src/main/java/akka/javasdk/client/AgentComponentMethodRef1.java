/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;

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
public interface AgentComponentMethodRef1<A1, R> extends ComponentMethodRef1<A1, R> {

  /**
   * Switch to a detailed reply mode that includes e.g. token usage information.
   *
   * @return A call representation that returns {@link akka.javasdk.agent.Agent.AgentReply}
   */
  AgentComponentInvokeOnlyMethodRef1<A1, R> withDetailedReply();
}
