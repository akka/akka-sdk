package com.example.eval.scorer.agentic;

// tag::imports[]
import akka.evalkit.core.domain.Observation;
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.metric.AlignmentMetric;
import akka.evalkit.core.metric.TurnFaithfulness;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;
// end::imports[]

/**
 * Wires the built-in TurnFaithfulness scorer to a customer-supplied Agent. The scorer
 * ships the system prompt; the customer picks the model, the provider and the cost
 * point.
 */
public class TurnFaithfulnessSample {

    // tag::wiring[]
    public RunOutcome score(Observation observation, ComponentClient componentClient) {
        String prompt = new TurnFaithfulness(null).systemPrompt(); // <1>

        AlignmentMetric.Assessor assessor = question -> componentClient // <2>
            .forAgent()
            .inSession(UUID.randomUUID().toString())                    // <3>
            .method(AlignmentJudge::assess)
            .invoke(new AlignmentJudge.Request(
                prompt, question.task() + "\n\n---\n\n" + question.against()));

        var scorer = new TurnFaithfulness(assessor); // <4>

        return scorer.score(observation); // <5>
    }
    // end::wiring[]

    // tag::judge[]
    @Component(id = "alignment-judge")
    public static class AlignmentJudge extends Agent {

        public record Request(String instructions, String material) {}

        public Effect<AlignmentMetric.Assessment> assess(Request request) {
            return effects()
                .memory(MemoryProvider.none())         // <1>
                .systemMessage(request.instructions())  // <2>
                .userMessage(request.material())
                .responseConformsTo(AlignmentMetric.Assessment.class) // <3>
                .thenReply();
        }
    }
    // end::judge[]

    // tag::stub-for-tests[]
    public RunOutcome scoreWithStub(Observation observation) {
        AlignmentMetric.Assessor stub = question ->
            new AlignmentMetric.Assessment(0.9, "the reply cited the passage verbatim"); // <1>

        var scorer = new TurnFaithfulness(stub); // <2>

        return scorer.score(observation);
    }
    // end::stub-for-tests[]
}
