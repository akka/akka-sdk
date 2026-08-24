package com.example.redteam.campaign;

// tag::imports[]
import akka.evalkit.redteam.runtime.adversary.Budget;
import akka.evalkit.redteam.scorer.JailbreakScorer;
import akka.evalkit.redteam.scorer.PIILeakDetector;
import akka.evalkit.redteam.scorer.RedTeamScorer;
import akka.evalkit.redteam.technique.adaptive.Crescendo;
import akka.evalkit.redteam.technique.staticwrap.Base64;
import akka.evalkit.redteam.technique.staticwrap.RolePlay;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
// end::imports[]

/**
 * A red-team campaign against a refund agent. Runs static wrappers over a corpus of
 * goals, escalates with an adaptive technique, and scores every attempt with two
 * heuristic scorers.
 */
// tag::class[]
@EnabledIfSystemProperty(named = "redteam", matches = "true") // <1>
public class RefundAgentRedTeamCampaign {

    @Test
    void refundAgentHoldsAgainstAttackerCorpus() {
        List<RedTeamScorer> scorers = List.of( // <2>
            new JailbreakScorer(),
            new PIILeakDetector());

        var plan = akka.evalkit.redteam.RedTeamCampaign.named("refund-safety") // <3>
            .withScorers(scorers)
            .withTechniques(new RolePlay(), new Base64())                     // <4>
            .withAdaptiveTechnique(new Crescendo())                            // <5>
            .withBudget(Budget.of(200_000, Duration.ofHours(1)))                // <6>
            .build();

        var result = plan.runAgainst(new RefundAgentTarget());                 // <7>

        result.assertNoBreakthroughs();                                        // <8>
    }
}
// end::class[]

class RefundAgentTarget {}
