package com.example.evaluator;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.List;
import java.util.concurrent.CompletionStage;

public class LedgerUsage {

  private final LedgerClient ledger;

  public LedgerUsage(LedgerClient ledger) {
    this.ledger = ledger;
  }

  void fetch(String interactionId) {
    // tag::fetch[]
    InteractionRecord record = ledger.getInteraction(interactionId); // <1>
    CompletionStage<InteractionRecord> async = ledger.getInteractionAsync(interactionId); // <2>
    // end::fetch[]
  }

  void evaluationsOf(String interactionId) {
    // tag::evaluations[]
    List<EvaluationRecord> evaluations = ledger.getEvaluations(interactionId); // <1>
    // end::evaluations[]
  }
}
