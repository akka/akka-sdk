package com.example.redteam.scorer.heuristic;

// tag::imports[]
import akka.evalkit.redteam.scorer.JailbreakScorer;
import akka.evalkit.redteam.scorer.RedTeamScorer;
// end::imports[]

/**
 * Reads the target's reply for two signals — strong-refusal language and
 * task-fulfilment language — and settles the clear cases. A reply carrying both signals
 * reads as INCONCLUSIVE so an agentic scorer picks up the borderline case.
 */
public class JailbreakSample {

    // tag::scorer[]
    public RedTeamScorer.ScoreResult score(String attackerPrompt, String targetReply) {
        var scorer = new JailbreakScorer(); // <1>

        return scorer.score(attackerPrompt, targetReply); // <2>
    }
    // end::scorer[]

    // tag::two-tier[]
    public RedTeamScorer.ScoreResult decide(
        String attackerPrompt,
        String targetReply,
        RedTeamScorer agenticFallback)
    {
        var heuristic = new JailbreakScorer().score(attackerPrompt, targetReply); // <1>

        if (heuristic.verdict() == RedTeamScorer.Verdict.INCONCLUSIVE) { // <2>
            return agenticFallback.score(attackerPrompt, targetReply);
        }

        return heuristic; // <3>
    }
    // end::two-tier[]
}
