package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.ledger.GuardrailDecisionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// tag::ledger-consumer[]
@Component(id = "denied-guardrail-alerts")
@Consume.FromLedger // <1>
public class DeniedGuardrailAlerts extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  public Effect onGuardrailDecision(GuardrailDecisionRecord decision) { // <2>
    if (decision.decision() != GuardrailDecisionRecord.Decision.DENY) {
      return effects().ignore(); // <3>
    }

    logger.warn(
      "Guardrail [{}] denied content of agent [{}], record [{}]",
      decision.name(),
      decision.agentComponentId(),
      decision.recordId() // <4>
    );
    return effects().done();
  }
}
// end::ledger-consumer[]
