/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;
import akka.javasdk.agent.Agent;
import java.util.concurrent.CompletionStage;

/**
 * One argument agent component call representation, returning a detailed reply including token
 * usage, not executed until invoked.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <A1> The argument type of the call
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface AgentComponentInvokeOnlyMethodRef1<A1, R> {

  Agent.AgentReply<R> invoke(A1 arg);

  CompletionStage<Agent.AgentReply<R>> invokeAsync(A1 arg);
}
