/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.evaluation;

import akka.javasdk.ledger.InteractionRecord;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A static collector that {@link LedgerBackedEvaluator} writes the interaction it fetched from the
 * ledger into, so {@link LedgerBackedEvaluatorIntegrationTest} can observe that the
 * runtime-triggered evaluation ran and saw the real record. Works because the embedded runtime runs
 * in a single JVM.
 */
public final class LedgerEvalProbe {

  private static final ConcurrentMap<String, InteractionRecord> fetched = new ConcurrentHashMap<>();

  public static void record(InteractionRecord record) {
    fetched.put(record.interactionId(), record);
  }

  public static Collection<InteractionRecord> all() {
    return List.copyOf(fetched.values());
  }

  public static void clear() {
    fetched.clear();
  }

  private LedgerEvalProbe() {}
}
