/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.util.List;
import java.util.Optional;

/**
 * One page of the records of a flow, as {@link LedgerClient#getFlowRecords(String)} returns it.
 *
 * @param records the records of the page, in the order they were recorded
 * @param nextPosition where the next page starts, for {@link LedgerClient#getFlowRecords(String,
 *     long)}; empty when the flow held no further record when it was read
 */
public record FlowPage(List<FlowRecord> records, Optional<Long> nextPosition) {}
