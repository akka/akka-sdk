/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Instant;

/**
 * A record of a flow, as {@link LedgerClient#getFlowRecords(String)} returns it. A later SDK
 * version can add kinds of records.
 *
 * <p>Not for user extension.
 */
public sealed interface FlowRecord
    permits InteractionRecord, ActivityRecord, GuardrailDecisionRecord, EvaluationRecord {

  /** When the record was recorded. */
  Instant timestamp();
}
