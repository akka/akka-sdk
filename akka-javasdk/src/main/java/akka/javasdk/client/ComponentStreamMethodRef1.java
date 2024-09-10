/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.stream.javadsl.Source;

/**
 * One argument component call representation, not executed until stream is materialized. Cannot be
 * deferred.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <A1> the argument type of the call
 * @param <R> The type of value returned by executing the call
 */
public interface ComponentStreamMethodRef1<A1, R> {

  Source<R, NotUsed> source(A1 arg);
}
