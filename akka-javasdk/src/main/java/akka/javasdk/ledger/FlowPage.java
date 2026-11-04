/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.List;
import java.util.Optional;

/**
 * One page of the records of a flow, as {@link LedgerClient#getFlowRecords(String)} returns it. A
 * position is not a count or an index of the records. Pass it only back to {@link
 * LedgerClient#getFlowRecords(String, long)}.
 *
 * @param records the records of the page, in the order they were recorded
 * @param nextPosition where the next page starts; empty when the flow held no further record when
 *     it was read
 * @param continuationPosition the position after the last record of the page, or the position the
 *     read started from when the page is empty. Read from it to get the records that were recorded
 *     after this page, also when the next position is empty.
 */
public record FlowPage(
    List<FlowRecord> records, Optional<Long> nextPosition, long continuationPosition) {}
