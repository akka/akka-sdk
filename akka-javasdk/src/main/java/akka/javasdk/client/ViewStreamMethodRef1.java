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
 * One argument streaming view query representation, the query is not executed until stream is
 * materialized. Cannot be deferred.
 *
 * <p>Not for user extension
 *
 * @param <A1> The type of the view input
 * @param <R> The type of entries in the view
 */
@DoNotInherit
public interface ViewStreamMethodRef1<A1, R> {
  Source<R, NotUsed> source(A1 arg);

  Source<EntryWithMetadata<R>, NotUsed> entriesSource(A1 arg);

  /**
   * @param arg Query parameters
   * @param updatedAfter Only return rows updated later than
   */
  Source<EntryWithMetadata<R>, NotUsed> entriesSource(A1 arg, Instant updatedAfter);
}
