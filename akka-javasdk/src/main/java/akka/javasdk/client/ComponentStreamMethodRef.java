/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.stream.javadsl.Source;

/**
 * Zero argument component call representation, not executed until stream is materialized. Cannot be
 * deferred.
 *
 * <p>Not for user extension or instantiation, returned by the SDK component client
 *
 * @param <R> The type of value returned by executing the call
 */
@DoNotInherit
public interface ComponentStreamMethodRef<R> {
  Source<R, NotUsed> source();
}
