/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.evaluator;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.PromptTemplate;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.impl.serialization.JsonSerializer;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.Junit5LogCapturing;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class ToxicityEvaluatorTest extends TestKitSupport {

  // Note that the Akka runtime representation doesn't have the label
  record RuntimeEvaluationResult(String explanation, boolean passed) {}

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
    assertThat(result.label()).isEqualTo("non-toxic");
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
    assertThat(result.label()).isEqualTo("toxic");
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
    assertThat(result.label()).isEqualTo("non-toxic");
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

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(ToxicityEvaluator::evaluate)
          .invoke("Have a nice day");
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
          "explanation" : "This is a nice greeting",
          "label" : "good"
        }
        """
            .stripIndent());

    try {
      componentClient
          .forAgent()
          .inSession(newSessionId())
          .method(ToxicityEvaluator::evaluate)
          .invoke("Have a nice day");
    } catch (Exception exc) {
      // FIXME Is it right to throw CorrelatedRuntimeException when the response mapper throws?
      //       The IllegalArgumentException isn't included as cause
      assertThat(exc.getMessage()).startsWith("Response mapping error");
    }
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
    assertThat(result.label()).isEqualTo("non-toxic");
    assertThat(result.passed()).isTrue();
  }

  @Test
  public void shouldSerializeToxicJsonResult() throws IOException {
    var result = new ToxicityEvaluator.Result("This is thretening", "toxic");
    var serializer = new JsonSerializer();
    var bytes = serializer.toBytes(result);
    var deserializedResult =
        serializer.objectMapper().readValue(bytes.bytes().toArray(), RuntimeEvaluationResult.class);

    assertThat(deserializedResult.passed()).isEqualTo(result.passed());
    assertThat(deserializedResult.explanation()).isEqualTo(result.explanation());
  }

  @Test
  public void shouldSerializeNonToxicJsonResult() throws IOException {
    var result = new ToxicityEvaluator.Result("This is a nice greeting", "non-toxic");
    var serializer = new JsonSerializer();
    var bytes = serializer.toBytes(result);
    var deserializedResult =
        serializer.objectMapper().readValue(bytes.bytes().toArray(), RuntimeEvaluationResult.class);

    assertThat(deserializedResult.passed()).isEqualTo(result.passed());
    assertThat(deserializedResult.explanation()).isEqualTo(result.explanation());
  }
}
