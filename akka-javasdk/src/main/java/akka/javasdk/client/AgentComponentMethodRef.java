/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.annotation.DoNotInherit;

/**
 * Zero argument agent component call representation.
 *
 * <p>Extends {@link ComponentMethodRef} with the ability to get a detailed reply including token
 * usage through {@link #withDetailedReply()}.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface AgentComponentMethodRef<R> extends ComponentMethodRef<R> {

  AgentComponentInvokeOnlyMethodRef<R> withDetailedReply();
}
