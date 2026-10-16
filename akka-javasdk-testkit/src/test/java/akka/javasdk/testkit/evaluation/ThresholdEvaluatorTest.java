/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.evaluation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.EvaluatorTestKit;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ThresholdEvaluatorTest {

  private final Subject subject =
      new Subject.Interaction("interaction-1", "support-agent", Optional.empty());

  @Test
  public void evaluatesWithTheGivenConfig() {
    var testKit =
        EvaluatorTestKit.of(ThresholdEvaluator::new)
            .withConfig(ConfigFactory.parseString("threshold = 0.7, score = 0.9"));

    assertTrue(testKit.evaluate(subject).getEvaluation().passed());

    var stricter = testKit.withConfig(ConfigFactory.parseString("threshold = 0.95, score = 0.9"));
    assertFalse(stricter.evaluate(subject).getEvaluation().passed());
  }

  @Test
  public void hasAnEmptyConfigByDefault() {
    var testKit = EvaluatorTestKit.of(ThresholdEvaluator::new);

    assertThrows(ConfigException.Missing.class, () -> testKit.evaluate(subject));
  }
}
