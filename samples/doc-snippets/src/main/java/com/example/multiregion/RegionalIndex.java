package com.example.multiregion;

import akka.Done;
import java.util.concurrent.CompletionStage;

/** A regional external search index, supplied through dependency injection. */
public interface RegionalIndex {
  /**
   * Upserts the order by order ID in the specified region's index. Repeating the same update must
   * be safe. Successful completion confirms the index accepted the update.
   */
  CompletionStage<Done> upsert(String region, OrderPlaced order);
}
