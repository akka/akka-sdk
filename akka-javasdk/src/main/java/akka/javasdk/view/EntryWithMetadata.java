/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.javasdk.Metadata;
import java.time.Instant;

/**
 * @param entry The actual entry from the view
 * @param metadata Additional metadata for the entry
 * @param <T> The type of the view entry
 */
public record EntryWithMetadata<T>(T entry, Metadata metadata) {
  /**
   * @return A timestamp when the view entry was last updated
   */
  public Instant lastUpdated() {
    return metadata
        .get("last-update")
        .map(Instant::parse)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No last-update found in the metadata, only guaranteed to be populated if the"
                        + " EntryWithMetadata instance was gotten from the entriesSource method on"
                        + " a view client."));
  }

  /**
   * Create a new view entry with the same metadata. Useful for returning something else than the
   * exact entry returned from the view, through {{Source#map}}, but still be able to let SSE
   * clients to resume from the last seen event.
   */
  public <O> EntryWithMetadata<O> withEntry(O newEntry) {
    return new EntryWithMetadata<>(newEntry, metadata);
  }
}
