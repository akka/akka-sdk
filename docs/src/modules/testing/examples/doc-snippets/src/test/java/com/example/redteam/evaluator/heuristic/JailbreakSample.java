package com.example.redteam.evaluator.heuristic;

// tag::imports[]
import akka.evalkit.redteam.evaluator.JailbreakEvaluator;
import akka.evalkit.redteam.evaluator.RedTeamEvaluator;
// end::imports[]

/**
 * Reads the target's reply for two signals — strong-refusal language and
 * task-fulfilment language — and settles the clear cases. A reply carrying both signals
 * reads as INCONCLUSIVE so an agentic evaluator picks up the borderline case.
 */
public class JailbreakSample {

    // tag::evaluator[]
    public RedTeamEvaluator.ScoreResult score(String attackerPrompt, String targetReply) {
        var evaluator = new JailbreakEvaluator(); // <1>

        return evaluator.score(attackerPrompt, targetReply); // <2>
    }
    // end::evaluator[]

    // tag::two-tier[]
    public RedTeamEvaluator.ScoreResult decide(
        String attackerPrompt,
        String targetReply,
        RedTeamEvaluator agenticFallback)
    {
        var heuristic = new JailbreakEvaluator().score(attackerPrompt, targetReply); // <1>

        if (heuristic.verdict() == RedTeamEvaluator.Verdict.INCONCLUSIVE) { // <2>
            return agenticFallback.score(attackerPrompt, targetReply);
        }

        return heuristic; // <3>
    }
    // end::two-tier[]
}
