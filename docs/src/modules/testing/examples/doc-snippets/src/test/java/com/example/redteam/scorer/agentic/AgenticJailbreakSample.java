package com.example.redteam.scorer.agentic;

// tag::imports[]
import akka.evalkit.redteam.scorer.AgenticJailbreakJudge;
import akka.evalkit.redteam.scorer.AgenticRedTeamScorer;
import akka.evalkit.redteam.scorer.RedTeamScorer;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;

import java.util.UUID;
// end::imports[]

/**
 * Wires the built-in AgenticJailbreakJudge to a customer-supplied Agent. The scorer
 * ships the system prompt and enforces the JSON verdict shape.
 */
public class AgenticJailbreakSample {

    // tag::wiring[]
    public RedTeamScorer.ScoreResult score(
        String attackerPrompt,
        String targetReply,
        ComponentClient componentClient)
    {
        String prompt = new AgenticJailbreakJudge(null).systemPrompt(); // <1>

        AgenticRedTeamScorer.Assessor assessor = (attack, response) -> componentClient // <2>
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(JailbreakJudgeAgent::assess)
            .invoke(new JailbreakJudgeAgent.Request(
                prompt,
                "Attacker prompt:\n" + attack + "\n\nModel response:\n" + response));

        var scorer = new AgenticJailbreakJudge(assessor); // <3>

        return scorer.score(attackerPrompt, targetReply); // <4>
    }
    // end::wiring[]

    // tag::judge[]
    @Component(id = "jailbreak-judge")
    public static class JailbreakJudgeAgent extends Agent {

        public record Request(String instructions, String material) {}

        public Effect<AgenticRedTeamScorer.Verdict> assess(Request request) {
            return effects()
                .memory(MemoryProvider.none())
                .systemMessage(request.instructions())
                .userMessage(request.material())
                .responseConformsTo(AgenticRedTeamScorer.Verdict.class)
                .thenReply();
        }
    }
    // end::judge[]

    // tag::stub-for-tests[]
    public RedTeamScorer.ScoreResult scoreWithStub(String attackerPrompt, String targetReply) {
        AgenticRedTeamScorer.Assessor stub = (prompt, response) ->
            new AgenticRedTeamScorer.Verdict(
                /* broke */ true,
                /* confidence */ 0.85,
                "target complied with the harmful request framed as fiction"); // <1>

        var scorer = new AgenticJailbreakJudge(stub); // <2>

        return scorer.score(attackerPrompt, targetReply);
    }
    // end::stub-for-tests[]
}
