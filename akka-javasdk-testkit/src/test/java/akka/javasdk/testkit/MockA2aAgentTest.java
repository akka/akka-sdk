/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.MockA2aAgent.Reply;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MockA2aAgentTest {

  private static final MockA2aAgent mock =
      MockA2aAgent.create("claims")
          .withSkill("status", "Looks up a claim", Reply.text("Claim 42 is paid"))
          .withSkill("details", "Gives the details of a claim", Reply.data(Map.of("amount", 120)))
          .withSkill(
              "review", "Reviews a claim", Reply.workingThenCompleted(Duration.ZERO, "Approved"))
          .withSkill(
              "slow",
              "A review that takes long",
              Reply.workingThenCompleted(Duration.ofHours(1), "Never"))
          .withSkill(
              "ask", "Asks for a policy", Reply.inputRequired("Which policy?", "Approved for P-1"))
          .withSkill("reject", "Rejects a claim", Reply.failed("Claim is not covered"))
          .withSkill("explode", "Always fails", Reply.error("Boom"))
          .start();

  private static final HttpClient http = HttpClient.newHttpClient();

  @BeforeEach
  void reset() {
    mock.reset();
  }

  @AfterAll
  static void stop() {
    mock.close();
  }

  @Test
  void servesTheAgentCard() throws Exception {
    var card = JsonSupport.getObjectMapper().readTree(get(mock.cardUrl()));

    assertThat(card.get("name").asText()).isEqualTo("claims");
    assertThat(card.get("skills"))
        .extracting(s -> s.get("id").asText())
        .startsWith("status", "details");
    assertThat(card.get("supportedInterfaces").get(0).get("protocolBinding").asText())
        .isEqualTo("JSONRPC");
  }

  @Test
  void repliesWithTextAndRecordsTheMessage() throws Exception {
    var result = send("status", "How is claim 42?", "ctx-1");

    assertThat(result.at("/message/parts/0/text").asText()).isEqualTo("Claim 42 is paid");
    assertThat(result.at("/message/contextId").asText()).isEqualTo("ctx-1");
    assertThat(mock.received())
        .containsExactly(
            new MockA2aAgent.Received("status", "How is claim 42?", Map.of(), "ctx-1", null));
  }

  @Test
  void repliesWithData() throws Exception {
    var result = send("details", "Details", null);

    assertThat(result.at("/message/parts/0/data/amount").asInt()).isEqualTo(120);
  }

  @Test
  void aMessageWithoutASkillGoesToTheFirstSkill() throws Exception {
    var result = send(null, "Anything", null);

    assertThat(result.at("/message/parts/0/text").asText()).isEqualTo("Claim 42 is paid");
    assertThat(mock.received().getFirst().skillId()).isEqualTo("status");
  }

  @Test
  void aTaskStaysWorkingThenCompletes() throws Exception {
    var slow = send("slow", "Review", null).get("task");
    assertThat(slow.at("/status/state").asText()).isEqualTo("TASK_STATE_WORKING");
    assertThat(
            rpc("GetTask", Map.of("id", slow.get("id").asText()))
                .at("/result/status/state")
                .asText())
        .isEqualTo("TASK_STATE_WORKING");

    var review = send("review", "Review", null).get("task");
    assertThat(review.at("/status/state").asText()).isEqualTo("TASK_STATE_WORKING");
    var done = rpc("GetTask", Map.of("id", review.get("id").asText())).get("result");
    assertThat(done.at("/status/state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(done.at("/artifacts/0/parts/0/text").asText()).isEqualTo("Approved");
  }

  @SuppressWarnings("unchecked")
  @Test
  void anInputRequiredTaskCompletesOnceAnswered() throws Exception {
    var asked = send("ask", "Review", "ctx-1").get("task");
    var taskId = asked.get("id").asText();
    assertThat(asked.at("/status/state").asText()).isEqualTo("TASK_STATE_INPUT_REQUIRED");
    assertThat(asked.at("/status/message/parts/0/text").asText()).isEqualTo("Which policy?");
    assertThat(rpc("GetTask", Map.of("id", taskId)).at("/result/status/state").asText())
        .isEqualTo("TASK_STATE_INPUT_REQUIRED");

    var answer = message("ask", "P-1", "ctx-1");
    ((Map<String, Object>) answer.get("message")).put("taskId", taskId);
    var answered = rpc("SendMessage", answer).at("/result/task");
    assertThat(answered.get("id").asText()).isEqualTo(taskId);
    assertThat(answered.at("/status/state").asText()).isEqualTo("TASK_STATE_WORKING");

    var done = rpc("GetTask", Map.of("id", taskId)).get("result");
    assertThat(done.at("/status/state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(done.at("/artifacts/0/parts/0/text").asText()).isEqualTo("Approved for P-1");
  }

  @Test
  void aTaskCanBeCanceledAndAnUnknownTaskIsAnError() throws Exception {
    var slow = send("slow", "Review", null).get("task");

    var canceled = rpc("CancelTask", Map.of("id", slow.get("id").asText())).get("result");
    assertThat(canceled.at("/status/state").asText()).isEqualTo("TASK_STATE_CANCELED");
    assertThat(rpc("GetTask", Map.of("id", "nope")).at("/error/code").asInt()).isEqualTo(-32001);
  }

  @Test
  void failsATaskOrTheRequest() throws Exception {
    var rejected = send("reject", "Reject", null).get("task");
    assertThat(rejected.at("/status/state").asText()).isEqualTo("TASK_STATE_FAILED");
    assertThat(rejected.at("/status/message/parts/0/text").asText())
        .isEqualTo("Claim is not covered");

    var error = rpc("SendMessage", message("explode", "Explode", null));
    assertThat(error.at("/error/message").asText()).isEqualTo("Boom");
  }

  @Test
  void answersAnUnknownSkillWithAnError() throws Exception {
    var error = rpc("SendMessage", message("missing", "Hello", null));

    assertThat(error.at("/error/code").asInt()).isEqualTo(-32602);
  }

  @Test
  void needsASkillBeforeStart() {
    assertThatThrownBy(() -> MockA2aAgent.create("empty").start())
        .isInstanceOf(IllegalStateException.class);
  }

  private JsonNode send(String skillId, String text, String contextId) throws Exception {
    return rpc("SendMessage", message(skillId, text, contextId)).get("result");
  }

  private static Map<String, Object> message(String skillId, String text, String contextId) {
    var message = new java.util.LinkedHashMap<String, Object>();
    message.put("messageId", "m-1");
    message.put("role", "ROLE_USER");
    message.put("parts", java.util.List.of(Map.of("text", text)));
    if (contextId != null) message.put("contextId", contextId);
    var params = new java.util.LinkedHashMap<String, Object>();
    params.put("message", message);
    if (skillId != null) params.put("metadata", Map.of("skillId", skillId));
    return params;
  }

  private JsonNode rpc(String method, Map<String, Object> params) throws Exception {
    var body =
        JsonSupport.getObjectMapper()
            .writeValueAsString(
                Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params));
    var uri = URI.create(mock.cardUrl().replace("/.well-known/agent-card.json", "/a2a"));
    var response =
        http.send(
            HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    return JsonSupport.getObjectMapper().readTree(response.body());
  }

  private String get(String url) throws Exception {
    return http.send(
            HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofString())
        .body();
  }
}
