/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.util.ByteString;
import akkajavasdk.components.a2a.A2aEchoAgent;
import akkajavasdk.components.a2a.A2aPushReceiverEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Long skills answered by the task workflow {@code A2aKitTaskWorkflow} of the test service. */
@ExtendWith(Junit5LogCapturing.class)
public class A2aTaskWorkflowTest extends TestKitSupport {

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

  private final TestModelProvider echoModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    echoModel.fixedResponse(new AiResponse("echo"));
    return TestKit.Settings.DEFAULT.withModelProvider(A2aEchoAgent.class, echoModel);
  }

  @Test
  public void publishesTaskWorkflowSkillsAndPushNotifications() throws Exception {
    var card = json(httpClient.GET("/.well-known/agent-card.json").invoke().body());
    assertThat(card.get("capabilities").get("pushNotifications").asBoolean()).isTrue();
    var ids = card.get("skills").findValuesAsText("id");
    assertThat(ids)
        .containsExactly("echo", "a2a-assistant", "a2a-streaming-agent", "kit-tasks", "ask");
    var ask = card.get("skills").get(4);
    assertThat(ask.get("tags").get(0).asText()).isEqualTo("input-required");
  }

  @Test
  public void pausesForInputAndResumesOnTheNextMessage() throws Exception {
    var first = send(message("ask", "start", null, null), null).get("task");
    assertThat(state(first)).isEqualTo("TASK_STATE_INPUT_REQUIRED");
    assertThat(statusText(first)).isEqualTo("What is your name?");

    var second =
        send(message("ask", "Ada", first.get("id").asText(), first.get("contextId").asText()), null)
            .get("task");
    assertThat(second.get("id").asText()).isEqualTo(first.get("id").asText());
    assertThat(state(second)).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(statusText(second)).isEqualTo("Hello, Ada");
    var roles = second.get("history").findValuesAsText("role");
    assertThat(roles).containsExactly("ROLE_USER", "ROLE_AGENT", "ROLE_USER", "ROLE_AGENT");

    var ended = rpc("SendMessage", Map.of("message", message("ask", "again", id(second), null)));
    assertThat(ended.get("error").get("code").asInt()).isEqualTo(-32004);
  }

  @Test
  public void streamsTheUpdatesOfATaskWorkflow() throws Exception {
    var first = stream(message("ask", "start", null, null));
    var task = first.get(0).get("result").get("task");
    assertThat(state(task)).isEqualTo("TASK_STATE_WORKING");
    var last = first.getLast().get("result").get("statusUpdate");
    assertThat(last.get("taskId").asText()).isEqualTo(id(task));
    assertThat(last.get("status").get("state").asText()).isEqualTo("TASK_STATE_INPUT_REQUIRED");

    var second = stream(message("ask", "Ada", id(task), null));
    var end = second.getLast().get("result").get("statusUpdate");
    assertThat(end.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(end.get("status").get("message").get("parts").get(0).get("text").asText())
        .isEqualTo("Hello, Ada");
  }

  @Test
  public void cancelsATaskMidWay() throws Exception {
    var started =
        send(message("kit-tasks", "wait", null, null), Map.of("returnImmediately", true))
            .get("task");
    assertThat(state(started)).isEqualTo("TASK_STATE_WORKING");
    var taskId = id(started);
    Awaitility.await()
        .atMost(10, SECONDS)
        .until(() -> "Waiting until canceled".equals(statusText(getTask(taskId))));

    var canceled = rpc("CancelTask", Map.of("id", taskId)).get("result");
    assertThat(state(canceled)).isEqualTo("TASK_STATE_CANCELED");
    assertThat(state(getTask(taskId))).isEqualTo("TASK_STATE_CANCELED");

    var again = rpc("CancelTask", Map.of("id", taskId)).get("error");
    assertThat(again.get("code").asInt()).isEqualTo(-32002);
  }

  @Test
  public void reportsDataAndFileArtifacts() throws Exception {
    var data = send(kitMessage("tck-artifact-data-" + UUID.randomUUID()), null).get("task");
    assertThat(state(data)).isEqualTo("TASK_STATE_COMPLETED");
    var dataPart = data.get("artifacts").get(0).get("parts").get(0).get("data");
    assertThat(dataPart.get("key").asText()).isEqualTo("value");
    assertThat(dataPart.get("count").asInt()).isEqualTo(42);

    var file = send(kitMessage("tck-artifact-file-" + UUID.randomUUID()), null).get("task");
    var filePart = file.get("artifacts").get(0).get("parts").get(0);
    assertThat(new String(Base64.getDecoder().decode(filePart.get("raw").asText())))
        .isEqualTo("tck");
    assertThat(filePart.get("mediaType").asText()).isEqualTo("text/plain");
    assertThat(filePart.get("filename").asText()).isEqualTo("output.txt");

    var url = send(kitMessage("tck-artifact-file-url-" + UUID.randomUUID()), null).get("task");
    assertThat(url.get("artifacts").get(0).get("parts").get(0).get("url").asText())
        .isEqualTo("https://example.com/output.txt");
  }

  @Test
  public void answersWithAMessageInsteadOfATask() throws Exception {
    var result = send(kitMessage("tck-message-response-" + UUID.randomUUID()), null);
    assertThat(result.has("task")).isFalse();
    var message = result.get("message");
    assertThat(message.get("role").asText()).isEqualTo("ROLE_AGENT");
    assertThat(message.get("parts").get(0).get("text").asText())
        .isEqualTo("Direct message response");
  }

  @Test
  public void deliversAPushNotificationAfterAFailedFirstAttempt() throws Exception {
    var receiver = "fail-once-" + UUID.randomUUID();
    var config =
        Map.of(
            "url",
            "http://localhost:" + testKit.getPort() + "/a2a-push-receiver/" + receiver,
            "token",
            "task-token",
            "authentication",
            Map.of("scheme", "Bearer", "credentials", "webhook-secret"));
    var task =
        send(
                message("kit-tasks", "hello", null, null),
                Map.of("taskPushNotificationConfig", config))
            .get("task");
    assertThat(state(task)).isEqualTo("TASK_STATE_COMPLETED");

    Awaitility.await()
        .atMost(30, SECONDS)
        .until(
            () ->
                A2aPushReceiverEndpoint.received.getOrDefault(receiver, List.of()).stream()
                    .anyMatch(r -> r.body().contains("TASK_STATE_COMPLETED")));
    assertThat(A2aPushReceiverEndpoint.attempts.get(receiver)).isGreaterThan(1);
    var delivered = A2aPushReceiverEndpoint.received.get(receiver).getFirst();
    assertThat(delivered.token()).isEqualTo("task-token");
    assertThat(delivered.authorization()).isEqualTo("Bearer webhook-secret");
    var body = mapper.readTree(delivered.body());
    assertThat(body.get("task").get("id").asText()).isEqualTo(id(task));
  }

  @Test
  public void createsGetsListsAndDeletesPushConfigurations() throws Exception {
    var task = send(message("ask", "start", null, null), null).get("task");
    var taskId = id(task);
    var url = "https://client.example/webhook";

    var created =
        rpc("CreateTaskPushNotificationConfig", Map.of("taskId", taskId, "url", url, "token", "t1"))
            .get("result");
    var configId = created.get("id").asText();
    assertThat(configId).isNotBlank();
    assertThat(created.get("taskId").asText()).isEqualTo(taskId);
    assertThat(created.get("url").asText()).isEqualTo(url);

    var fetched =
        // ProtoJSON clients may send the proto field names
        rpc("GetTaskPushNotificationConfig", Map.of("task_id", taskId, "id", configId))
            .get("result");
    assertThat(fetched.get("token").asText()).isEqualTo("t1");

    var overHttp =
        httpClient
            .POST("/a2a/tasks/" + taskId + "/pushNotificationConfigs")
            .addHeader("Authorization", token("a"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, bytes(Map.of("id", "second", "url", url)))
            .invoke();
    assertThat(overHttp.status()).isEqualTo(StatusCodes.OK);

    var listed =
        rpc("ListTaskPushNotificationConfigs", Map.of("taskId", taskId, "pageSize", 1))
            .get("result");
    assertThat(listed.get("configs")).hasSize(1);
    var nextPage =
        rpc(
                "ListTaskPushNotificationConfigs",
                Map.of("taskId", taskId, "pageToken", listed.get("nextPageToken").asText()))
            .get("result");
    assertThat(nextPage.get("configs").get(0).get("id").asText()).isEqualTo("second");

    var deleted = rpc("DeleteTaskPushNotificationConfig", Map.of("taskId", taskId, "id", configId));
    assertThat(deleted.get("result").isObject()).isTrue();
    var listedOverHttp =
        json(
            httpClient
                .GET("/a2a/tasks/" + taskId + "/pushNotificationConfigs")
                .addHeader("Authorization", token("a"))
                .invoke()
                .body());
    assertThat(listedOverHttp.get("configs").findValuesAsText("id")).containsExactly("second");

    var missing =
        rpc("GetTaskPushNotificationConfig", Map.of("taskId", taskId, "id", configId)).get("error");
    assertThat(missing.get("code").asInt()).isEqualTo(-32001);
    var badUrl =
        rpc("CreateTaskPushNotificationConfig", Map.of("taskId", taskId, "url", "ftp://x"))
            .get("error");
    assertThat(badUrl.get("code").asInt()).isEqualTo(-32602);
    var otherSubject =
        rpcAs(token("mallory"), "ListTaskPushNotificationConfigs", Map.of("taskId", taskId))
            .get("error");
    assertThat(otherSubject.get("code").asInt()).isEqualTo(-32001);
  }

  // ---------------------------------------------------------------------------------------------

  private JsonNode send(Map<String, Object> message, Map<String, Object> configuration)
      throws Exception {
    var params = new LinkedHashMap<String, Object>();
    params.put("message", message);
    if (configuration != null) params.put("configuration", configuration);
    var response = rpc("SendMessage", params);
    assertThat(response.has("error")).as(response.toString()).isFalse();
    return response.get("result");
  }

  // The JSON-RPC responses of a SendStreamingMessage, one per server-sent event
  private List<JsonNode> stream(Map<String, Object> message) throws Exception {
    var body =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token("a"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(
                    Map.of(
                        "jsonrpc",
                        "2.0",
                        "id",
                        1,
                        "method",
                        "SendStreamingMessage",
                        "params",
                        Map.of("message", message))))
            .withTimeout(Duration.ofSeconds(30))
            .invoke()
            .body()
            .utf8String();
    var events = new java.util.ArrayList<JsonNode>();
    for (var line : body.split("\n")) {
      if (line.startsWith("data:")) events.add(mapper.readTree(line.substring(5).trim()));
    }
    assertThat(events).as(body).isNotEmpty();
    return events;
  }

  private JsonNode getTask(String taskId) throws Exception {
    return rpc("GetTask", Map.of("id", taskId)).get("result");
  }

  private JsonNode rpc(String method, Object params) throws Exception {
    return rpcAs(token("a"), method, params);
  }

  private JsonNode rpcAs(String token, String method, Object params) throws Exception {
    var response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token)
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params)))
            .withTimeout(Duration.ofSeconds(30))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    return json(response.body());
  }

  private static Map<String, Object> message(
      String skillId, String text, String taskId, String contextId) {
    var message = new LinkedHashMap<String, Object>();
    message.put("messageId", UUID.randomUUID().toString());
    message.put("role", "ROLE_USER");
    message.put("parts", List.of(Map.of("text", text)));
    if (skillId != null) message.put("metadata", Map.of("skillId", skillId));
    if (taskId != null) message.put("taskId", taskId);
    if (contextId != null) message.put("contextId", contextId);
    return message;
  }

  private static Map<String, Object> kitMessage(String messageId) {
    var message = message("kit-tasks", "kit", null, null);
    message.put("messageId", messageId);
    return message;
  }

  private static String id(JsonNode task) {
    return task.get("id").asText();
  }

  private static String state(JsonNode task) {
    return task.get("status").get("state").asText();
  }

  private static String statusText(JsonNode task) {
    var message = task.get("status").get("message");
    return message == null ? null : message.get("parts").get(0).get("text").asText();
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

  // A token without signature, which the runtime accepts in dev mode and in tests
  private static String token(String subject) {
    var header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims =
        Base64.getEncoder().encodeToString(bytes(Map.of("iss", "a2a-test-issuer", "sub", subject)));
    return "Bearer " + header + "." + claims;
  }
}
