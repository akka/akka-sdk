/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.List;

/**
 * One page of the records of a flow, as {@link LedgerClient#getFlowRecords(String)} returns it. A
 * position is not a count or an index of the records. Pass it only back to {@link
 * LedgerClient#getFlowRecords(String, long)}.
 *
 * @param records the records of the page, in the order they were recorded
 * @param nextPosition where the next page starts: the position after the last record that the read
 *     reached, also when the client left that record out of the page, or the position the read
 *     started from when the read reached no record. A page that is empty because the client left
 *     out all its records can therefore have a next position after the start. A read from it also
 *     returns the records that were recorded after this page was read.
 * @param hasMore {@code true} when the flow held more records after this page when it was read
 */
public record FlowPage(List<FlowRecord> records, long nextPosition, boolean hasMore) {}
