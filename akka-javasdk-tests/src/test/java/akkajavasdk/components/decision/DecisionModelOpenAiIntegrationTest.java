/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.decision.TriageEndpoint.ChoiceResult;
import akkajavasdk.components.decision.TriageEndpoint.PredicateResult;
import akkajavasdk.components.decision.TriageEndpoint.ScoreResult;
import akkajavasdk.components.decision.TriageEndpoint.StandInCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Calls a decision model through an endpoint, the injected client and the runtime, against a local
 * HTTP server that answers like the OpenAI Decisions API.
 */
@ExtendWith(Junit5LogCapturing.class)
public class DecisionModelOpenAiIntegrationTest extends TestKitSupport {

  private record CapturedRequest(String path, String authorization, JsonNode body) {}

  private record StandInResponse(int status, String body) {}

  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicReference<StandInResponse> nextResponse = new AtomicReference<>();
  private final BlockingQueue<CapturedRequest> capturedRequests = new LinkedBlockingQueue<>();
  private HttpServer standIn;
  private String baseUrl;

  @BeforeAll
  public void startStandIn() throws IOException {
    standIn = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    standIn.createContext(
        "/",
        exchange -> {
          var body = mapper.readTree(exchange.getRequestBody());
          capturedRequests.add(
              new CapturedRequest(
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestHeaders().getFirst("Authorization"),
                  body));
          var response = nextResponse.get();
          var bytes = response.body().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(response.status(), bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    standIn.start();
    baseUrl = "http://127.0.0.1:" + standIn.getAddress().getPort() + "/v1";
  }

  @AfterAll
  public void stopStandIn() {
    if (standIn != null) standIn.stop(0);
  }

  private <T> T call(String path, String input, Class<T> resultType) {
    return httpClient
        .POST(path)
        .withRequestBody(new StandInCall(baseUrl, input))
        .responseBodyAs(resultType)
        .invoke()
        .body();
  }

  @Test
  public void sendChoiceQuestionAndReadTheAnswer() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "gpt-6-luna-2026-10-15",
              "answers": [
                {
                  "type": "choice",
                  "name": "route",
                  "choice": "billing",
                  "probabilities": [
                    { "value": "billing", "probability": 0.95 },
                    { "value": "technical", "probability": 0.05 }
                  ],
                  "confidence": 0.93
                }
              ],
              "usage": {"input_tokens": 120, "output_tokens": 0}
            }
            """));

    var result =
        call("/triage/openai/route", "Charged twice for my subscription", ChoiceResult.class);

    assertThat(result.selected()).isEqualTo("billing");
    assertThat(result.confidence()).isEqualTo(0.93);
    assertThat(result.probabilities())
        .containsExactlyInAnyOrderEntriesOf(Map.of("billing", 0.95, "technical", 0.05));
    assertThat(result.model()).isEqualTo("gpt-6-luna-2026-10-15");

    var captured = capturedRequests.take();
    assertThat(captured.path()).isEqualTo("/v1/decisions");
    assertThat(captured.authorization()).isEqualTo("Bearer test-key");
    assertThat(captured.body().get("model").asText()).isEqualTo("gpt-6-luna");
    assertThat(captured.body().get("input").isTextual()).isTrue();
    assertThat(captured.body().get("input").asText())
        .isEqualTo("Charged twice for my subscription");
    var question = captured.body().get("questions").get(0);
    assertThat(question.get("type").asText()).isEqualTo("choice");
    assertThat(question.get("name").asText()).isEqualTo("route");
    assertThat(question.get("instructions").asText())
        .isEqualTo("Which team should handle this ticket?");
    var choices = question.get("choices");
    assertThat(choices.size()).isEqualTo(2);
    assertThat(choices.get(0).get("value").asText()).isEqualTo("billing");
    assertThat(choices.get(0).get("description").asText())
        .isEqualTo("Payments, invoicing, refunds");
    assertThat(choices.get(1).get("value").asText()).isEqualTo("technical");
    assertThat(choices.get(1).get("description").asText()).isEqualTo("Bugs, outages, integrations");
  }

  @Test
  public void sendScoreQuestionAndReadTheAnswer() throws InterruptedException {
    // the probabilities are not in level order, the client returns them in level order
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "gpt-6-luna-2026-10-15",
              "answers": [
                {
                  "type": "score",
                  "name": "severity",
                  "score": 1.1,
                  "probabilities": [
                    { "value": 1, "label": "Broken, but a workaround exists", "probability": 0.7 },
                    { "value": 0, "label": "Cosmetic, no impact on function", "probability": 0.1 },
                    { "value": 2, "label": "Blocking, no workaround", "probability": 0.2 }
                  ],
                  "confidence": 0.55
                }
              ],
              "usage": {"input_tokens": 80, "output_tokens": 0}
            }
            """));

    var result = call("/triage/openai/severity", "The checkout page is broken", ScoreResult.class);

    assertThat(result.value()).isEqualTo(1.1);
    assertThat(result.confidence()).isEqualTo(0.55);
    assertThat(result.probabilities()).containsExactly(0.1, 0.7, 0.2);

    var captured = capturedRequests.take();
    var question = captured.body().get("questions").get(0);
    assertThat(question.get("type").asText()).isEqualTo("score");
    assertThat(question.get("name").asText()).isEqualTo("severity");
    var levels = question.get("levels");
    assertThat(levels.size()).isEqualTo(3);
    assertThat(levels.get(0).get("label").asText()).isEqualTo("Cosmetic, no impact on function");
    assertThat(levels.get(1).get("label").asText()).isEqualTo("Broken, but a workaround exists");
    assertThat(levels.get(2).get("label").asText()).isEqualTo("Blocking, no workaround");
  }

  @Test
  public void sendPredicateQuestionWithCriteriaInTheInstructions() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "gpt-6-luna-2026-10-15",
              "answers": [
                {"type": "predicate", "name": "urgent", "probability": 0.87}
              ],
              "usage": {"input_tokens": 60, "output_tokens": 0}
            }
            """));

    var result =
        call("/triage/openai/urgent", "Need an answer before tomorrow", PredicateResult.class);

    assertThat(result.probability()).isEqualTo(0.87);

    var captured = capturedRequests.take();
    var question = captured.body().get("questions").get(0);
    assertThat(question.get("type").asText()).isEqualTo("predicate");
    assertThat(question.get("name").asText()).isEqualTo("urgent");
    assertThat(question.get("instructions").asText())
        .isEqualTo(
            "Does the customer need a reply today?\n\n"
                + "True when: The customer states a deadline of today or earlier\n"
                + "False when: There is no deadline requirement");
  }

  @Test
  public void failWithModelExceptionOnRefusal() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "gpt-6-luna-2026-10-15",
              "answers": [
                {"type": "refusal", "name": "urgent"}
              ],
              "usage": {"input_tokens": 60, "output_tokens": 0}
            }
            """));

    var result = call("/triage/openai/failure-class", "any", String.class);

    assertThat(result).isEqualTo("ModelException");
    assertThat(capturedRequests.take().path()).isEqualTo("/v1/decisions");
  }

  @Test
  public void failWithRateLimitExceptionOnTooManyRequests() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            429,
            """
            {"error": {"message": "Rate limit reached", "type": "requests"}}
            """));

    var result = call("/triage/openai/failure-class", "any", String.class);

    assertThat(result).isEqualTo("RateLimitException");
    assertThat(capturedRequests.take().path()).isEqualTo("/v1/decisions");
  }
}
