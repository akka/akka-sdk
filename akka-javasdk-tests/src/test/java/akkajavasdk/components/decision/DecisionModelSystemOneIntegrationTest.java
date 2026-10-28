/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.decision;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.decision.TriageEndpoint.ChoiceResult;
import akkajavasdk.components.decision.TriageEndpoint.ScoreResult;
import akkajavasdk.components.decision.TriageEndpoint.StandInCall;
import akkajavasdk.components.decision.TriageEndpoint.YesNoResult;
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
 * HTTP server that answers like the SystemOne API.
 */
@ExtendWith(Junit5LogCapturing.class)
public class DecisionModelSystemOneIntegrationTest extends TestKitSupport {

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
    baseUrl = "http://127.0.0.1:" + standIn.getAddress().getPort();
  }

  @AfterAll
  public void stopStandIn() {
    if (standIn != null) standIn.stop(0);
  }

  private <T> T call(String path, String state, Class<T> resultType) {
    return httpClient
        .POST(path)
        .withRequestBody(new StandInCall(baseUrl, state))
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
              "model": "jev-1.13.0",
              "answers": {
                "route": {
                  "type": "choice",
                  "choice": "billing",
                  "probabilities": {"billing": 0.9, "technical": 0.1},
                  "confidence": 0.8
                }
              },
              "usage": {"input_tokens": 120, "output_tokens": 9}
            }
            """));

    var result =
        call("/triage/system-one/route", "Charged twice for my subscription", ChoiceResult.class);

    assertThat(result.selected()).isEqualTo("billing");
    assertThat(result.confidence()).isEqualTo(0.8);
    assertThat(result.probabilities())
        .containsExactlyInAnyOrderEntriesOf(Map.of("billing", 0.9, "technical", 0.1));
    assertThat(result.model()).isEqualTo("jev-1.13.0");

    var captured = capturedRequests.take();
    assertThat(captured.path()).isEqualTo("/v1/systemone");
    assertThat(captured.authorization()).isEqualTo("Bearer test-key");
    assertThat(captured.body().get("model").asText()).isEqualTo("jev-latest");
    assertThat(captured.body().get("state").asText())
        .isEqualTo("Charged twice for my subscription");
    var question = captured.body().get("questions").get("route");
    assertThat(question.get("type").asText()).isEqualTo("choice");
    assertThat(question.get("instructions").asText())
        .isEqualTo("Which team should handle this ticket?");
    assertThat(question.get("criteria").get("billing").asText())
        .isEqualTo("Payments, invoicing, refunds");
    assertThat(question.get("criteria").get("technical").asText())
        .isEqualTo("Bugs, outages, integrations");
  }

  @Test
  public void sendScoreQuestionAndReadTheAnswer() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "jev-1.13.0",
              "answers": {
                "severity": {
                  "type": "score",
                  "score": 1.05,
                  "probabilities": {"0": 0.15, "1": 0.65, "2": 0.2},
                  "confidence": 0.58
                }
              },
              "usage": {"input_tokens": 80, "output_tokens": 7}
            }
            """));

    var result =
        call("/triage/system-one/severity", "The checkout page is broken", ScoreResult.class);

    assertThat(result.value()).isEqualTo(1.05);
    assertThat(result.confidence()).isEqualTo(0.58);
    assertThat(result.probabilities()).containsExactly(0.15, 0.65, 0.2);

    var captured = capturedRequests.take();
    var question = captured.body().get("questions").get("severity");
    assertThat(question.get("type").asText()).isEqualTo("score");
    assertThat(question.get("criteria").isArray()).isTrue();
    assertThat(question.get("criteria").size()).isEqualTo(3);
    assertThat(question.get("criteria").get(0).asText())
        .isEqualTo("Cosmetic, no impact on function");
  }

  @Test
  public void sendYesNoQuestionAndReadTheAnswer() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            200,
            """
            {
              "model": "jev-1.13.0",
              "answers": {
                "urgent": {"type": "noul", "noul": 0.87}
              },
              "usage": {"input_tokens": 60, "output_tokens": 3}
            }
            """));

    var result =
        call("/triage/system-one/urgent", "Need an answer before tomorrow", YesNoResult.class);

    assertThat(result.probability()).isEqualTo(0.87);

    var captured = capturedRequests.take();
    var question = captured.body().get("questions").get("urgent");
    assertThat(question.get("type").asText()).isEqualTo("noul");
    assertThat(question.get("criteria").get("true").asText())
        .isEqualTo("The customer states a deadline of today or earlier");
    assertThat(question.get("criteria").get("false").asText())
        .isEqualTo("There is no deadline requirement");
  }

  @Test
  public void failWithRateLimitExceptionOnTooManyRequests() throws InterruptedException {
    nextResponse.set(
        new StandInResponse(
            429,
            """
            {"error": "rate limited"}
            """));

    var result = call("/triage/system-one/failure-class", "any", String.class);

    assertThat(result).isEqualTo("RateLimitException");
    capturedRequests.take();
  }
}
