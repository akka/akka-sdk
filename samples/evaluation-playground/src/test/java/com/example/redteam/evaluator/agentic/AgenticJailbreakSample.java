package com.example.redteam.evaluator.agentic;

// tag::imports[]
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.redkit.evaluator.AgenticJailbreakJudge;
import akka.redkit.evaluator.AgenticRedTeamEvaluator;
import akka.redkit.evaluator.RedTeamEvaluator;

import java.util.UUID;
// end::imports[]

/**
 * Wires the built-in AgenticJailbreakJudge to a customer-supplied Agent. The evaluator
 * ships the system prompt and enforces the JSON verdict shape.
 */
public class AgenticJailbreakSample {

    // tag::wiring[]
    public RedTeamEvaluator.ScoreResult score(
        String attackerPrompt,
        String targetReply,
        ComponentClient componentClient)
    {
        String prompt = new AgenticJailbreakJudge(null).systemPrompt(); // <1>

        AgenticRedTeamEvaluator.Assessor assessor = (attack, response) -> componentClient // <2>
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(JailbreakJudgeAgent::assess)
            .invoke(new JailbreakJudgeAgent.Request(
                prompt,
                "Attacker prompt:\n" + attack + "\n\nModel response:\n" + response));

        var evaluator = new AgenticJailbreakJudge(assessor); // <3>

        return evaluator.score(attackerPrompt, targetReply); // <4>
    }
    // end::wiring[]

    // tag::judge[]
    @Component(id = "jailbreak-judge")
    public static class JailbreakJudgeAgent extends Agent {

        public record Request(String instructions, String material) {}

        public Effect<AgenticRedTeamEvaluator.Assessment> assess(Request request) {
            return effects()
                .memory(MemoryProvider.none())
                .systemMessage(request.instructions())
                .userMessage(request.material())
                .responseConformsTo(AgenticRedTeamEvaluator.Assessment.class)
                .thenReply();
        }
    }
    // end::judge[]

    // tag::stub-for-tests[]
    public RedTeamEvaluator.ScoreResult scoreWithStub(String attackerPrompt, String targetReply) {
        AgenticRedTeamEvaluator.Assessor stub = (prompt, response) ->
            new AgenticRedTeamEvaluator.Assessment(
                /* broke */ true,
                /* confidence */ 0.85,
                "target complied with the harmful request framed as fiction"); // <1>

        var evaluator = new AgenticJailbreakJudge(stub); // <2>

        return evaluator.score(attackerPrompt, targetReply);
    }
    // end::stub-for-tests[]
}
