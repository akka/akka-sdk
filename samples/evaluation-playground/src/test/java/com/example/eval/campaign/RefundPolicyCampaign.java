package com.example.eval.campaign;

import static org.assertj.core.api.Assertions.assertThat;

// tag::imports[]
import akka.eval.contract.evaluation.EvaluationResult;
import akka.eval.contract.evaluation.EvaluatorKind;
import akka.eval.contract.evaluation.Rubric;
import akka.evalkit.core.application.ExperimentRunner;
import akka.evalkit.core.domain.EvalCase;
import akka.evalkit.core.domain.EvalSetup;
import akka.evalkit.core.domain.ExperimentSetup;
import akka.evalkit.core.domain.Lanes;
import akka.evalkit.core.domain.Rubrics;
import akka.evalkit.core.domain.SystemUnderTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

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
    var cases = List.of( // <2>
      new EvalCase(
        "refund-timing",
        Optional.empty(),
        EvalSetup.Fixture.named("signed-in"),
        "when do I get my refund?",
        "the reply states a 30-day refund window"
      )
    );

    Rubric rubric = Rubrics.load("case-judge", 3);
    var setup = new ExperimentSetup( // <3>
      "refund-policy",
      cases,
      Lanes.of(2),
      rubric
    );

    SystemUnderTest target = new RefundAgentTarget(); // <4>

    ExperimentRunner.Judge judge = (transcript, r) -> // <5>
      EvaluationResult.scored(
        "case-judge",
        "3",
        EvaluatorKind.JUDGE,
        0.9,
        true,
        "states 30 days"
      );

    var result = ExperimentRunner.run(setup, target, judge); // <6>

    assertThat(result.report().passRate()).isGreaterThan(0.9); // <7>
  }
}

// end::class[]

class RefundAgentTarget implements SystemUnderTest {

  @Override
  public Prepared prepare(EvalSetup evalSetup) {
    return new Prepared.Ready("s", "");
  }

  @Override
  public Reply submit(String sessionId, String userText) {
    return Reply.of("the refund window is 30 days");
  }

  @Override
  public Map<String, String> fixtures() {
    return Map.of("signed-in", "signed in");
  }
}
