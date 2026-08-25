package com.example.eval.campaign;

// tag::imports[]
import akka.evalkit.core.application.CampaignRunner;
import akka.evalkit.core.domain.CampaignPlan;
import akka.evalkit.core.domain.Grade;
import akka.evalkit.core.domain.Lanes;
import akka.evalkit.core.domain.Precursor;
import akka.evalkit.core.domain.Rubric;
import akka.evalkit.core.domain.RunOutcome;
import akka.evalkit.core.domain.Scenario;
import akka.evalkit.core.domain.SystemUnderTest;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
// end::imports[]

/**
 * The whole first-campaign shape in one place. A campaign is a JUnit test gated on
 * -Deval=true so `mvn verify` compiles it without running it.
 */
// tag::class[]
@EnabledIfSystemProperty(named = "eval", matches = "true") // <1>
public class RefundPolicyCampaign {

    @Test
    void refundPolicyHoldsUp() {
        var scenarios = List.of( // <2>
            new Scenario(
                "refund-timing",
                Optional.empty(),
                Precursor.Fixture.named("signed-in"),
                "when do I get my refund?",
                "the reply states a 30-day refund window"));

        var plan = new CampaignPlan( // <3>
            "refund-policy",
            scenarios,
            Lanes.of(2),
            Rubric.load("scenario-judge", 3));

        SystemUnderTest target = new RefundAgentTarget(); // <4>

        CampaignRunner.Judge judge = (transcript, rubric) -> // <5>
            new RunOutcome.Scored(Grade.of(transcript.scenarioName(), rubric, 9, "states 30 days"));

        var result = CampaignRunner.run(plan, target, judge); // <6>

        assertThat(result.report().passRate()).isGreaterThan(0.9); // <7>
    }
}
// end::class[]

class RefundAgentTarget implements SystemUnderTest {
    @Override public Prepared prepare(Precursor precursor) { return new Prepared.Ready("s", ""); }
    @Override public Reply submit(String sessionId, String userText) { return Reply.of("the refund window is 30 days"); }
    @Override public java.util.Map<String, String> fixtures() { return java.util.Map.of("signed-in", "signed in"); }
}
