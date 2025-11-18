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
 * One argument streaming view query representation, the query is not executed until stream is
 * materialized. Cannot be deferred.
 *
 * <p>Not for user extension
 *
 * @param <A1> The type of the view input
 * @param <R> The type of entries in the view
 */
@DoNotInherit
public interface ViewStreamMethodRef1<A1, R> extends ComponentStreamMethodRef1<A1, R> {

  /**
   * @return A stream of view entries.
   */
  Source<R, NotUsed> source(A1 arg);

  /**
   * @return A stream of view entries, including metadata.
   */
  Source<EntryWithMetadata<R>, NotUsed> entriesSource(A1 arg);

  /**
   * @param arg Query parameters
   * @param updatedAfter If not empty, only return rows updated later than this time.
   * @return A stream of view entries, including metadata.
   */
  Source<EntryWithMetadata<R>, NotUsed> entriesSource(A1 arg, Optional<Instant> updatedAfter);
}
