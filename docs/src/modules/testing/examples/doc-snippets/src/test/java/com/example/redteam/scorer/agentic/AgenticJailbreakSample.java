package com.example.redteam.scorer.agentic;

// tag::imports[]
import akka.evalkit.redteam.scorer.AgenticJailbreakJudge;
import akka.evalkit.redteam.scorer.AgenticRedTeamScorer;
import akka.evalkit.redteam.scorer.RedTeamScorer;
import akka.javasdk.agent.Agent;
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
        Agent judgeAgent)
    {
        AgenticRedTeamScorer.Assessor assessor = (prompt, response) ->
            judgeAgent.query("Attacker prompt:\n" + prompt
                    + "\n\nModel response:\n" + response)                             // <1>
                .systemMessage(new AgenticJailbreakJudge(null).systemPrompt())        // <2>
                .responseConformsTo(AgenticRedTeamScorer.Verdict.class)               // <3>
                .invoke();

        var scorer = new AgenticJailbreakJudge(assessor); // <4>

        return scorer.score(attackerPrompt, targetReply); // <5>
    }
    // end::wiring[]

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
