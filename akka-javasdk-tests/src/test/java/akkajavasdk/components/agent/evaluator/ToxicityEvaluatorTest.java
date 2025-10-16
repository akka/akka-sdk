/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ToxicityEvaluatorTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(ToxicityEvaluator.class, testModelProvider);
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
          "explanation" : "This is a nice greeting",
          "label" : "non-toxic"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(ToxicityEvaluator::evaluate)
            .invoke("Have a nice day");

    assertThat(result.explanation()).isEqualTo("This is a nice greeting");
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldFailEvaluation() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is threatening",
          "label" : "toxic"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(ToxicityEvaluator::evaluate)
            .invoke("Human … Please die");

    assertThat(result.explanation()).isEqualTo("This is threatening");
    assertThat(result.passed()).isFalse();
  }

  @Test
  public void shouldAcceptResponseWithoutExplanation() {
    testModelProvider.fixedResponse(
        """
        {
          "label" : "non-toxic"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(ToxicityEvaluator::evaluate)
            .invoke("Have a nice day");

    assertThat(result.explanation()).isNull();
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldNotAcceptResponseWithoutLabel() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is a nice greeting"
        }
        """
            .stripIndent());

    assertThatThrownBy(
            () ->
                componentClient
                    .forAgent()
                    .inSession(newSessionId())
                    .method(ToxicityEvaluator::evaluate)
                    .invoke("Have a nice day"))
        .hasMessageContaining("Response mapping error");
  }

  @Test
  public void shouldNotAcceptResponseWithWrongLabel() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is a nice greeting",
          "label" : "good"
        }
        """
            .stripIndent());

    assertThatThrownBy(
            () ->
                componentClient
                    .forAgent()
                    .inSession(newSessionId())
                    .method(ToxicityEvaluator::evaluate)
                    .invoke("Have a nice day"))
        .hasMessageContaining("Response mapping error");
  }

  @Test
  public void shouldUsePromptTemplate() {
    componentClient
        .forEventSourcedEntity("toxicity-evaluator.user")
        .method(PromptTemplate::update)
        .invoke("Updated user prompt");

    testModelProvider
        .whenMessage("Updated user prompt")
        .reply(
            """
            {
              "explanation" : "This is a nice greeting",
              "label" : "non-toxic"
            }
            """
                .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(ToxicityEvaluator::evaluate)
            .invoke("Have a nice day");

    assertThat(result.explanation()).isEqualTo("This is a nice greeting");
    assertThat(result.passed()).isTrue();
  }
}
