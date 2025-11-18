/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.client;

import akka.NotUsed;
import akka.annotation.DoNotInherit;
import akka.javasdk.view.EntryWithMetadata;
import akka.stream.javadsl.Source;
import java.time.Instant;
import java.util.Optional;

/**
 * Zero argument component call representation, query is not executed until stream is materialized.
 * Cannot be deferred.
 *
 * <p>Not for user extension
 *
 * @param <R> The type of entries in the view
 */
@DoNotInherit
public interface ViewStreamMethodRef<R> extends ComponentStreamMethodRef<R> {
  /**
   * @return A stream of view entries.
   */
  Source<R, NotUsed> source();

  /**
   * @return A stream of view entries, including metadata.
   */
  Source<EntryWithMetadata<R>, NotUsed> entriesSource();

  /**
   * @param updatedAfter If not empty, only return rows updated later than this time.
   * @return A stream of view entries, including metadata.
   */
  Source<EntryWithMetadata<R>, NotUsed> entriesSource(Optional<Instant> updatedAfter);
}
