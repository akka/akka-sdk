/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.view.EntryWithMetadata;
import akka.stream.javadsl.Source;
import java.time.Instant;

/**
 * Zero argument component call representation, query is not executed until stream is materialized.
 * Cannot be deferred.
 *
 * <p>Not for user extension
 *
 * @param <R> The type of entries in the view
 */
@DoNotInherit
public interface ViewStreamMethodRef<R> {
  Source<R, NotUsed> source();

  Source<EntryWithMetadata<R>, NotUsed> entriesSource();

  Source<EntryWithMetadata<R>, NotUsed> entriesSource(Instant updatedAfter);
}
