/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.view;

import akka.javasdk.Metadata;
import java.time.Instant;

public record EntryWithMetadata<T>(T entry, Metadata metadata) {
  /**
   * @return A timestamp when the view entry was last updated
   */
  public Instant lastUpdated() {
    // FIXME better error if for some reason missing
    return metadata.get("last-update").map(Instant::parse).get();
  }
}
