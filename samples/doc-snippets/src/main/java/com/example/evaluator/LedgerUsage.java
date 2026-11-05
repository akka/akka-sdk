package com.example.evaluator;

import akka.javasdk.ledger.EvaluationRecord;
import akka.javasdk.ledger.FlowPage;
import akka.javasdk.ledger.FlowRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.LedgerClient;
import java.util.ArrayList;
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

  void evaluationsOfRun(String workflowId) {
    // tag::workflow-evaluations[]
    List<EvaluationRecord> evaluations = ledger.getWorkflowEvaluations(
      "evaluated-agent-team",
      workflowId
    );
    // end::workflow-evaluations[]
  }

  String flowTranscript(String flowId) {
    // tag::flow[]
    FlowPage page = ledger.getFlowRecords(flowId); // <1>
    List<FlowRecord> records = new ArrayList<>(page.records());
    while (page.hasMore()) { // <2>
      page = ledger.getFlowRecords(flowId, page.nextPosition());
      records.addAll(page.records());
    }
    String transcript = FlowRecord.transcript(records); // <3>
    // end::flow[]
    return transcript;
  }
}
