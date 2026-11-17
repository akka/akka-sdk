/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Reads what an attempt left in the ledger, waiting for records the runtime writes after the agent
 * has replied.
 */
final class LedgerReader {

  private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
  private static final Duration FAILED_CALL_WAIT = Duration.ofSeconds(2);

  private final LedgerClient ledger;
  private final Duration timeout;

  /**
   * @param timeout how long to wait for a record
   */
  LedgerReader(LedgerClient ledger, Duration timeout) {
    this.ledger = ledger;
    this.timeout = timeout;
  }

  // The reply carries the id before the ledger necessarily holds the record.
  Optional<InteractionRecord> interaction(String interactionId) {
    return poll(
        () -> {
          try {
            return Optional.of(ledger.getInteraction(interactionId));
          } catch (NoSuchElementException e) {
            return Optional.empty();
          }
        });
  }

  // A failed call carries no interaction id. The session is fresh per attempt, so the newest
  // interaction of the agent in it is the attempt's, when the runtime recorded one. A call that
  // failed before the agent ran recorded none, so the wait is short.
  Optional<InteractionRecord> newestInteraction(String agentComponentId, String sessionId) {
    var wait = timeout.compareTo(FAILED_CALL_WAIT) < 0 ? timeout : FAILED_CALL_WAIT;
    return poll(
        () -> ledger.getInteractions(agentComponentId, sessionId).stream().findFirst(), wait);
  }

  List<GuardrailDecisionRecord> guardrails(InteractionRecord interaction) {
    return ledger.getGuardrailDecisions(interaction.interactionId());
  }

  /**
   * The evaluations recorded about the interaction, once there is one per expected evaluator or the
   * timeout has passed.
   */
  List<EvaluationRecord> awaitEvaluations(String interactionId, Set<String> expectedEvaluators) {
    return poll(() -> {
          var records = ledger.getEvaluations(interactionId);
          var recorded =
              records.stream()
                  .map(EvaluationRecord::evaluatorComponentId)
                  .collect(Collectors.toSet());
          return recorded.containsAll(expectedEvaluators)
              ? Optional.of(records)
              : Optional.<List<EvaluationRecord>>empty();
        })
        .orElseGet(() -> ledger.getEvaluations(interactionId));
  }

  private <T> Optional<T> poll(Supplier<Optional<T>> read) {
    return poll(read, timeout);
  }

  private <T> Optional<T> poll(Supplier<Optional<T>> read, Duration timeout) {
    var deadline = Instant.now().plus(timeout);
    while (true) {
      var result = read.get();
      if (result.isPresent() || !Instant.now().isBefore(deadline)) return result;
      try {
        Thread.sleep(POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return Optional.empty();
      }
    }
  }
}
