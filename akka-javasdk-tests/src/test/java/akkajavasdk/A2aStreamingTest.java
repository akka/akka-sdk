/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.util.ByteString;
import akkajavasdk.components.a2a.A2aAssistantAgent;
import akkajavasdk.components.a2a.A2aEchoAgent;
import akkajavasdk.components.a2a.A2aStreamingAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Streaming, ListTasks and CancelTask of the A2A endpoint, on both protocol bindings. */
@ExtendWith(Junit5LogCapturing.class)
public class A2aStreamingTest extends TestKitSupport {

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

  // An assistant request whose text is a key of this map waits for its latch
  private static final Map<String, CountDownLatch> blockers = new ConcurrentHashMap<>();

  private final TestModelProvider echoModel = new TestModelProvider();
  private final TestModelProvider assistantModel = new TestModelProvider();
  private final TestModelProvider streamingModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    echoModel.fixedResponse(
        input -> {
          var text = input.content();
          if (text.equals("fail")) throw new RuntimeException("model unavailable");
          return new AiResponse("echo: " + text);
        });
    assistantModel.fixedResponse(
        input -> {
          var latch = blockers.get(input.content());
          if (latch != null) {
            try {
              latch.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          }
          return new AiResponse("Done: " + input.content());
        });
    streamingModel.fixedResponse(
        input -> {
          return new AiResponse("Streamed answer for " + input.content());
        });
    return TestKit.Settings.DEFAULT
        .withModelProvider(A2aEchoAgent.class, echoModel)
        .withModelProvider(A2aAssistantAgent.class, assistantModel)
        .withModelProvider(A2aStreamingAgent.class, streamingModel);
  }

  // ---------------------------------------------------------------------------------------------
  // SendStreamingMessage

  @Test
  public void streamsAnAgentReplyOverJsonRpc() throws Exception {
    var response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(rpc("SendStreamingMessage", sendParams("echo", "hello", null, false))))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.httpResponse().entity().getContentType().toString())
        .startsWith("text/event-stream");

    var events = events(response.body());
    for (JsonNode event : events) {
      assertThat(event.get("jsonrpc").asText()).isEqualTo("2.0");
      assertThat(event.get("id").asInt()).isEqualTo(1);
    }
    var results = events.stream().map(e -> e.get("result")).toList();
    var task = results.get(0).get("task");
    assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_WORKING");
    assertThat(task.get("history")).hasSize(1);

    var artifact = results.get(1).get("artifactUpdate");
    assertThat(artifact.get("taskId").asText()).isEqualTo(task.get("id").asText());
    assertThat(artifact.get("lastChunk").asBoolean()).isTrue();
    assertThat(artifact.get("artifact").get("parts").get(0).get("text").asText())
        .isEqualTo("echo: hello");

    var last = results.get(results.size() - 1).get("statusUpdate");
    assertThat(last.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");

    var stored =
        jsonRpc("GetTask", Map.of("id", task.get("id").asText()), token("alice")).get("result");
    assertThat(stored.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(stored.get("artifacts").get(0).get("artifactId").asText())
        .isEqualTo(artifact.get("artifact").get("artifactId").asText());
  }

  @Test
  public void streamsAnAgentReplyOverHttpJson() throws Exception {
    var response =
        httpClient
            .POST("/a2a/message:stream")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, bytes(sendParams("echo", "over rest", null, false)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    var events = events(response.body());
    assertThat(events.get(0).has("task")).isTrue();
    assertThat(
            events
                .get(1)
                .get("artifactUpdate")
                .get("artifact")
                .get("parts")
                .get(0)
                .get("text")
                .asText())
        .isEqualTo("echo: over rest");
    assertThat(
            events.get(events.size() - 1).get("statusUpdate").get("status").get("state").asText())
        .isEqualTo("TASK_STATE_COMPLETED");
  }

  @Test
  public void streamsTheTokensOfAStreamingAgentAsOneArtifact() throws Exception {
    var response =
        httpClient
            .POST("/a2a/message:stream")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(sendParams("a2a-streaming-agent", "tokens", null, false)))
            .invoke();
    var events = events(response.body());

    var chunks =
        events.stream()
            .filter(e -> e.has("artifactUpdate"))
            .map(e -> e.get("artifactUpdate"))
            .toList();
    assertThat(chunks).isNotEmpty();
    assertThat(chunks.get(0).path("append").asBoolean()).isFalse();
    assertThat(chunks.get(chunks.size() - 1).get("lastChunk").asBoolean()).isTrue();
    var text = new StringBuilder();
    for (int i = 0; i < chunks.size(); i++) {
      if (i > 0) assertThat(chunks.get(i).get("append").asBoolean()).isTrue();
      if (i < chunks.size() - 1) assertThat(chunks.get(i).path("lastChunk").asBoolean()).isFalse();
      text.append(chunks.get(i).get("artifact").get("parts").get(0).get("text").asText());
    }
    assertThat(text.toString()).isEqualTo("Streamed answer for tokens");
    assertThat(chunks.stream().map(c -> c.get("artifact").get("artifactId").asText()).distinct())
        .hasSize(1);

    var taskId = events.get(0).get("task").get("id").asText();
    var stored = jsonRpc("GetTask", Map.of("id", taskId), token("alice")).get("result");
    assertThat(stored.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(stored.get("artifacts").get(0).get("parts").get(0).get("text").asText())
        .isEqualTo("Streamed answer for tokens");
    assertThat(
            events.get(events.size() - 1).get("statusUpdate").get("status").get("state").asText())
        .isEqualTo("TASK_STATE_COMPLETED");
  }

  @Test
  public void endsAStreamWithAFailedStatusWhenTheAgentFails() throws Exception {
    var response =
        httpClient
            .POST("/a2a/message:stream")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, bytes(sendParams("echo", "fail", null, false)))
            .invoke();
    var events = events(response.body());
    var last = events.get(events.size() - 1).get("statusUpdate");
    assertThat(last.get("status").get("state").asText()).isEqualTo("TASK_STATE_FAILED");
    assertThat(events.stream().noneMatch(e -> e.has("artifactUpdate"))).isTrue();
  }

  @Test
  public void answersAStreamingRequestThatCannotStartWithAnError() throws Exception {
    var jsonRpcError =
        jsonRpc("SendStreamingMessage", sendParams("nope", "hi", null, false), token("a"))
            .get("error");
    assertThat(jsonRpcError.get("code").asInt()).isEqualTo(-32602);

    var response =
        httpClient
            .POST("/a2a/message:stream")
            .addHeader("Authorization", token("a"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, bytes(sendParams("nope", "hi", null, false)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
    assertThat(json(response.body()).get("error").get("status").asText())
        .isEqualTo("INVALID_ARGUMENT");
  }

  // ---------------------------------------------------------------------------------------------
  // SubscribeToTask

  @Test
  public void subscribesToARunningTaskOverBothBindings() throws Exception {
    for (var binding : List.of("jsonrpc", "http")) {
      var text = "block " + binding + UUID.randomUUID();
      var latch = new CountDownLatch(1);
      blockers.put(text, latch);
      var task = startRunningTask("a2a-assistant", text, "sub");
      var taskId = task.get("id").asText();
      assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_WORKING");

      var subscription =
          CompletableFuture.supplyAsync(
              () ->
                  binding.equals("jsonrpc")
                      ? httpClient
                          .POST("/a2a")
                          .addHeader("Authorization", token("sub"))
                          .withRequestBody(
                              ContentTypes.APPLICATION_JSON,
                              bytes(rpc("SubscribeToTask", Map.of("id", taskId))))
                          .invoke()
                      : httpClient
                          .GET("/a2a/tasks/" + taskId + ":subscribe")
                          .addHeader("Authorization", token("sub"))
                          .invoke());
      // The subscription reads the task before the agent answers
      Thread.sleep(1500);
      latch.countDown();

      var events = events(subscription.get(30, TimeUnit.SECONDS).body());
      var items =
          events.stream().map(e -> binding.equals("jsonrpc") ? e.get("result") : e).toList();
      assertThat(items.get(0).get("task").get("status").get("state").asText())
          .isEqualTo("TASK_STATE_WORKING");
      assertThat(items.stream().anyMatch(i -> i.has("artifactUpdate"))).isTrue();
      var last = items.get(items.size() - 1).get("statusUpdate");
      assertThat(last.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
      var versions = items.stream().filter(i -> i.has("statusUpdate")).count();
      assertThat(versions).isEqualTo(1);
    }
  }

  @Test
  public void refusesToSubscribeToAnEndedOrUnknownTask() throws Exception {
    var done =
        jsonRpc("SendMessage", sendParams("echo", "done", null, false), token("sub"))
            .get("result")
            .get("task");
    var ended = jsonRpc("SubscribeToTask", Map.of("id", done.get("id").asText()), token("sub"));
    assertThat(ended.get("error").get("code").asInt()).isEqualTo(-32004);

    var unknown = jsonRpc("SubscribeToTask", Map.of("id", "no-such-task"), token("sub"));
    assertThat(unknown.get("error").get("code").asInt()).isEqualTo(-32001);

    var rest =
        httpClient
            .GET("/a2a/tasks/" + done.get("id").asText() + ":subscribe")
            .addHeader("Authorization", token("sub"))
            .invoke();
    assertThat(rest.status()).isEqualTo(StatusCodes.BAD_REQUEST);

    var otherSubject =
        jsonRpc("SubscribeToTask", Map.of("id", done.get("id").asText()), token("mallory"));
    assertThat(otherSubject.get("error").get("code").asInt()).isEqualTo(-32001);
  }

  // ---------------------------------------------------------------------------------------------
  // ListTasks

  @Test
  public void listsTasksWithFiltersAndPages() throws Exception {
    var subject = "lister-" + UUID.randomUUID();
    var token = token(subject);
    var context = UUID.randomUUID().toString();
    var ids = new ArrayList<String>();
    for (var text : List.of("one", "two", "three")) {
      var task =
          jsonRpc("SendMessage", sendParams("echo", text, context, false), token)
              .get("result")
              .get("task");
      ids.add(task.get("id").asText());
    }
    var failed =
        jsonRpc("SendMessage", sendParams("echo", "fail", context, false), token)
            .get("result")
            .get("task");
    var elsewhere =
        jsonRpc("SendMessage", sendParams("echo", "other", null, false), token)
            .get("result")
            .get("task");

    // The view is updated after the tasks are written
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jsonRpc("ListTasks", Map.of("contextId", context), token)
                            .get("result")
                            .get("totalSize")
                            .asInt())
                    .isEqualTo(4));

    var firstPage =
        jsonRpc("ListTasks", Map.of("contextId", context, "pageSize", 3), token).get("result");
    assertThat(firstPage.get("tasks")).hasSize(3);
    assertThat(firstPage.get("totalSize").asInt()).isEqualTo(4);
    assertThat(firstPage.get("pageSize").asInt()).isEqualTo(3);
    assertThat(firstPage.get("nextPageToken").asText()).isNotEmpty();
    // The most recently changed task comes first
    assertThat(firstPage.get("tasks").get(0).get("id").asText())
        .isEqualTo(failed.get("id").asText());
    // Artifacts are left out unless requested
    assertThat(firstPage.get("tasks").get(1).has("artifacts")).isFalse();

    var secondPage =
        jsonRpc(
                "ListTasks",
                Map.of(
                    "contextId",
                    context,
                    "pageSize",
                    3,
                    "pageToken",
                    firstPage.get("nextPageToken").asText(),
                    "includeArtifacts",
                    true),
                token)
            .get("result");
    assertThat(secondPage.get("tasks")).hasSize(1);
    assertThat(secondPage.get("nextPageToken").asText()).isEmpty();
    assertThat(secondPage.get("tasks").get(0).get("artifacts")).hasSize(1);
    var seen = new ArrayList<String>();
    firstPage.get("tasks").forEach(t -> seen.add(t.get("id").asText()));
    secondPage.get("tasks").forEach(t -> seen.add(t.get("id").asText()));
    assertThat(seen)
        .containsExactlyInAnyOrder(ids.get(0), ids.get(1), ids.get(2), failed.get("id").asText());

    var onlyFailed =
        jsonRpc("ListTasks", Map.of("contextId", context, "status", "TASK_STATE_FAILED"), token)
            .get("result");
    assertThat(onlyFailed.get("tasks")).hasSize(1);
    assertThat(onlyFailed.get("totalSize").asInt()).isEqualTo(1);

    var everything = jsonRpc("ListTasks", Map.of(), token).get("result");
    assertThat(everything.get("totalSize").asInt()).isEqualTo(5);
    assertThat(everything.get("tasks").toString()).contains(elsewhere.get("id").asText());

    var future = Instant.now().plusSeconds(3600).toString();
    var none = jsonRpc("ListTasks", Map.of("statusTimestampAfter", future), token).get("result");
    assertThat(none.get("tasks")).isEmpty();
    var past = Instant.now().minusSeconds(3600).toString();
    var all = jsonRpc("ListTasks", Map.of("statusTimestampAfter", past), token).get("result");
    assertThat(all.get("totalSize").asInt()).isEqualTo(5);

    var otherSubject = jsonRpc("ListTasks", Map.of("contextId", context), token("someone-else"));
    assertThat(otherSubject.get("result").get("tasks")).isEmpty();

    var rest =
        httpClient
            .GET(
                "/a2a/tasks?contextId="
                    + context
                    + "&pageSize=2&status=TASK_STATE_COMPLETED&historyLength=1")
            .addHeader("Authorization", token)
            .invoke();
    assertThat(rest.status()).isEqualTo(StatusCodes.OK);
    var restPage = json(rest.body());
    assertThat(restPage.get("tasks")).hasSize(2);
    assertThat(restPage.get("totalSize").asInt()).isEqualTo(3);
    assertThat(restPage.get("nextPageToken").asText()).isEqualTo("2");
    assertThat(restPage.get("tasks").get(0).get("history")).hasSize(1);
  }

  @Test
  public void refusesInvalidListTasksParameters() throws Exception {
    var token = token("lister");
    assertThat(jsonRpc("ListTasks", Map.of("pageSize", 0), token).get("error").get("code").asInt())
        .isEqualTo(-32602);
    assertThat(
            jsonRpc("ListTasks", Map.of("pageSize", 101), token).get("error").get("code").asInt())
        .isEqualTo(-32602);
    assertThat(
            jsonRpc("ListTasks", Map.of("pageToken", "not a token"), token)
                .get("error")
                .get("code")
                .asInt())
        .isEqualTo(-32602);
  }

  // ---------------------------------------------------------------------------------------------
  // CancelTask

  @Test
  public void cancelsARunningTaskOverBothBindings() throws Exception {
    for (var binding : List.of("jsonrpc", "http")) {
      var text = "block " + binding + UUID.randomUUID();
      var latch = new CountDownLatch(1);
      blockers.put(text, latch);
      var task = startRunningTask("a2a-assistant", text, "canceler");
      var taskId = task.get("id").asText();

      JsonNode canceled;
      if (binding.equals("jsonrpc")) {
        canceled = jsonRpc("CancelTask", Map.of("id", taskId), token("canceler")).get("result");
      } else {
        var response =
            httpClient
                .POST("/a2a/tasks/" + taskId + ":cancel")
                .addHeader("Authorization", token("canceler"))
                .invoke();
        assertThat(response.status()).isEqualTo(StatusCodes.OK);
        canceled = json(response.body());
      }
      assertThat(canceled.get("status").get("state").asText()).isEqualTo("TASK_STATE_CANCELED");

      // The agent answers afterwards, and the answer does not replace the cancel
      latch.countDown();
      Thread.sleep(1500);
      var stored = jsonRpc("GetTask", Map.of("id", taskId), token("canceler")).get("result");
      assertThat(stored.get("status").get("state").asText()).isEqualTo("TASK_STATE_CANCELED");
      assertThat(stored.has("artifacts")).isFalse();

      var again = jsonRpc("CancelTask", Map.of("id", taskId), token("canceler"));
      assertThat(again.get("error").get("code").asInt()).isEqualTo(-32002);
    }
  }

  @Test
  public void refusesToCancelAnEndedOrUnknownTask() throws Exception {
    var done =
        jsonRpc("SendMessage", sendParams("echo", "done", null, false), token("canceler"))
            .get("result")
            .get("task");
    var ended = jsonRpc("CancelTask", Map.of("id", done.get("id").asText()), token("canceler"));
    assertThat(ended.get("error").get("code").asInt()).isEqualTo(-32002);
    assertThat(ended.get("error").get("data").get(0).get("reason").asText())
        .isEqualTo("TASK_NOT_CANCELABLE");

    var rest =
        httpClient
            .POST("/a2a/tasks/" + done.get("id").asText() + ":cancel")
            .addHeader("Authorization", token("canceler"))
            .invoke();
    assertThat(rest.status()).isEqualTo(StatusCodes.BAD_REQUEST);

    var unknown = jsonRpc("CancelTask", Map.of("id", "no-such-task"), token("canceler"));
    assertThat(unknown.get("error").get("code").asInt()).isEqualTo(-32001);

    var otherSubject =
        jsonRpc("CancelTask", Map.of("id", done.get("id").asText()), token("mallory"));
    assertThat(otherSubject.get("error").get("code").asInt()).isEqualTo(-32001);
  }

  // ---------------------------------------------------------------------------------------------

  // A task that stays working until the latch of its text is released
  private JsonNode startRunningTask(String skill, String text, String subject) throws Exception {
    return jsonRpc("SendMessage", sendParams(skill, text, null, true), token(subject))
        .get("result")
        .get("task");
  }

  private JsonNode jsonRpc(String method, Object params, String token) throws Exception {
    var response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token)
            .withRequestBody(ContentTypes.APPLICATION_JSON, bytes(rpc(method, params)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    return json(response.body());
  }

  // The JSON of each data line of a server-sent events body
  private static List<JsonNode> events(ByteString body) throws Exception {
    var events = new ArrayList<JsonNode>();
    for (var line : body.utf8String().split("\n")) {
      if (line.startsWith("data:")) events.add(mapper.readTree(line.substring(5).trim()));
    }
    assertThat(events).as("events in body: " + body.utf8String()).isNotEmpty();
    return events;
  }

  private static Map<String, Object> rpc(String method, Object params) {
    return Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
  }

  private static Map<String, Object> sendParams(
      String skillId, String text, String contextId, boolean returnImmediately) {
    var message = new LinkedHashMap<String, Object>();
    message.put("messageId", UUID.randomUUID().toString());
    message.put("role", "ROLE_USER");
    message.put("parts", List.of(Map.of("text", text)));
    message.put("metadata", Map.of("skillId", skillId));
    if (contextId != null) message.put("contextId", contextId);
    var params = new LinkedHashMap<String, Object>();
    params.put("message", message);
    if (returnImmediately) params.put("configuration", Map.of("returnImmediately", true));
    return params;
  }

  private static byte[] bytes(Object value) {
    try {
      return mapper.writeValueAsBytes(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static JsonNode json(ByteString body) throws Exception {
    return mapper.readTree(body.utf8String());
  }

  private static String token(String subject) {
    var header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims =
        Base64.getEncoder().encodeToString(bytes(Map.of("iss", "a2a-test-issuer", "sub", subject)));
    return "Bearer " + header + "." + claims;
  }
}
