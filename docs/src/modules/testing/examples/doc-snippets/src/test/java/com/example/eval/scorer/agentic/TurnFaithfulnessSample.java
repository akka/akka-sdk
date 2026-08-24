package com.example.eval.scorer.agentic;

// tag::imports[]
import akka.evalkit.core.domain.Observation;
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.metric.AlignmentMetric;
import akka.evalkit.core.metric.TurnFaithfulness;
import akka.javasdk.agent.Agent;
// end::imports[]

/**
 * Wires the built-in TurnFaithfulness scorer to a customer-supplied Agent. The scorer
 * ships the system prompt; the customer picks the model, the provider and the cost
 * point.
 */
public class TurnFaithfulnessSample {

    // tag::wiring[]
    public RunOutcome score(Observation observation, Agent judge) {
        AlignmentMetric.Assessor assessor = question ->
            judge.query(question.task() + "\n\n---\n\n" + question.against()) // <1>
                 .systemMessage(new TurnFaithfulness(null).systemPrompt())     // <2>
                 .responseConformsTo(AlignmentMetric.Assessment.class)         // <3>
                 .invoke();

        var scorer = new TurnFaithfulness(assessor); // <4>

        return scorer.score(observation); // <5>
    }
    // end::wiring[]

    // tag::stub-for-tests[]
    public RunOutcome scoreWithStub(Observation observation) {
        AlignmentMetric.Assessor stub = question ->
            new AlignmentMetric.Assessment(0.9, "the reply cited the passage verbatim"); // <1>

        var scorer = new TurnFaithfulness(stub); // <2>

        return scorer.score(observation);
    }
    // end::stub-for-tests[]
}
