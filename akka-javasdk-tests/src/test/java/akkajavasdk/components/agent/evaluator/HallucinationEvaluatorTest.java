/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.evaluator.HallucinationEvaluator;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class HallucinationEvaluatorTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(
        HallucinationEvaluator.class, testModelProvider);
  }

  @AfterEach
  public void afterEach() {
    testModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  @Test
  public void shouldPassEvaluation() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This LGTM",
          "label" : "factual"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(HallucinationEvaluator::evaluate)
            .invoke(
                new HallucinationEvaluator.EvaluationRequest(
                    "What is the color of the sky?",
                    "The sky is blue because blue light, with its shorter waves...",
                    "blue"));

    assertThat(result.explanation()).isEqualTo("This LGTM");
    assertThat(result.label()).isEqualTo("factual");
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldFailEvaluation() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is fake",
          "label" : "hallucinated"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(HallucinationEvaluator::evaluate)
            .invoke(
                new HallucinationEvaluator.EvaluationRequest(
                    "What is the color of the sky?",
                    "The sky is blue because blue light, with its shorter waves...",
                    "space is black"));

    assertThat(result.explanation()).isEqualTo("This is fake");
    assertThat(result.label()).isEqualTo("hallucinated");
    assertThat(result.passed()).isFalse();
  }
}
