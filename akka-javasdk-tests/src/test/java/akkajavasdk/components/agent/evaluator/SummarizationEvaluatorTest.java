/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.agent.evaluator.SummarizationEvaluator;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class SummarizationEvaluatorTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(
        SummarizationEvaluator.class, testModelProvider);
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
          "explanation" : "This is a good summary",
          "label" : "good"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest("today is sunny", "sunny"));

    assertThat(result.explanation()).isEqualTo("This is a good summary");
    assertThat(result.label()).isEqualTo("good");
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldFailEvaluation() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is missing the point",
          "label" : "bad"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SummarizationEvaluator::evaluate)
            .invoke(
                new SummarizationEvaluator.EvaluationRequest(
                    "today is rainy", "same as yesterday"));

    assertThat(result.explanation()).isEqualTo("This is missing the point");
    assertThat(result.label()).isEqualTo("bad");
    assertThat(result.passed()).isFalse();
  }

  @Test
  public void shouldAcceptResponseWithoutExplanation() {
    testModelProvider.fixedResponse(
        """
        {
          "label" : "good"
        }
        """
            .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest("today is sunny", "sunny"));

    assertThat(result.explanation()).isNull();
    assertThat(result.label()).isEqualTo("good");
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldNotAcceptResponseWithoutLabel() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is a good summary"
        }
        """
            .stripIndent());

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(SummarizationEvaluator::evaluate)
          .invoke(new SummarizationEvaluator.EvaluationRequest("today is sunny", "sunny"));
    } catch (Exception exc) {
      // FIXME Is it right to throw CorrelatedRuntimeException when the response mapper throws?
      //       The IllegalArgumentException isn't included as cause
      assertThat(exc.getMessage()).startsWith("Response mapping error");
    }
  }

  @Test
  public void shouldNotAcceptResponseWithoutWrongLabel() {
    testModelProvider.fixedResponse(
        """
        {
          "explanation" : "This is a good summary",
          "label" : "good"
        }
        """
            .stripIndent());

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(SummarizationEvaluator::evaluate)
          .invoke(new SummarizationEvaluator.EvaluationRequest("today is sunny", "sunny"));
    } catch (Exception exc) {
      // FIXME Is it right to throw CorrelatedRuntimeException when the response mapper throws?
      //       The IllegalArgumentException isn't included as cause
      assertThat(exc.getMessage()).startsWith("Response mapping error");
    }
  }

  @Test
  public void shouldUsePromptTemplate() {
    componentClient
        .forEventSourcedEntity("summarization-evaluator.user")
        .method(PromptTemplate::update)
        .invoke("Updated user prompt");

    testModelProvider
        .whenMessage("Updated user prompt")
        .reply(
            """
            {
              "explanation" : "This is a good summary",
              "label" : "good"
            }
            """
                .stripIndent());

    var result =
        componentClient
            .forAgent()
            .inSession(newSessionId())
            .method(SummarizationEvaluator::evaluate)
            .invoke(new SummarizationEvaluator.EvaluationRequest("today is sunny", "sunny"));

    assertThat(result.explanation()).isEqualTo("This is a good summary");
    assertThat(result.label()).isEqualTo("good");
    assertThat(result.passed()).isTrue();
  }
}
