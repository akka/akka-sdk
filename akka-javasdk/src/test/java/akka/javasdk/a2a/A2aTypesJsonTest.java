/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.a2a;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.javasdk.JsonSupport;
import akka.javasdk.impl.a2a.A2aErrorCode;
import akka.javasdk.impl.a2a.A2aMethods;
import akka.javasdk.impl.a2a.JsonRpcError;
import akka.javasdk.impl.a2a.JsonRpcRequest;
import akka.javasdk.impl.a2a.JsonRpcResponse;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class A2aTypesJsonTest {

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

  // The example files start with a comment that names their source in the specification.
  private static final ObjectMapper exampleReader =
      JsonSupport.getObjectMapper().copy().enable(JsonParser.Feature.ALLOW_COMMENTS);

  private static final Map<String, Class<?>> TYPES =
      Map.ofEntries(
          Map.entry("SendMessageRequest", A2aSendMessageRequest.class),
          Map.entry("SendMessageResponse", A2aSendMessageResponse.class),
          Map.entry("StreamResponse", A2aStreamResponse.class),
          Map.entry("ListTasksResponse", A2aListTasksResponse.class),
          Map.entry("AgentCard", A2aAgentCard.class),
          Map.entry("AgentCardSignature", A2aAgentCard.Signature.class),
          Map.entry("Message", A2aMessage.class),
          Map.entry("Artifact", A2aArtifact.class),
          Map.entry("JsonRpcRequest", JsonRpcRequest.class),
          Map.entry("JsonRpcResponse", JsonRpcResponse.class));

  static Stream<Path> exampleFiles() throws Exception {
    Path dir = Paths.get(A2aTypesJsonTest.class.getResource("/a2a").toURI());
    try (Stream<Path> files = Files.list(dir)) {
      return files.sorted().toList().stream();
    }
  }

  private static Class<?> typeOf(Path file) {
    String[] parts = file.getFileName().toString().split("\\.");
    // name.Type.json, where the name itself can contain dots, such as 6.1-example
    return TYPES.get(parts[parts.length - 2]);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("exampleFiles")
  void roundTripsEverySpecificationExample(Path file) throws IOException {
    Class<?> type = typeOf(file);
    assertTrue(type != null, "no type registered for " + file.getFileName());
    String json = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(json.startsWith("// Source:"), "the example names its source");

    JsonNode expected = exampleReader.readTree(json);
    Object value = exampleReader.readValue(json, type);
    JsonNode written = mapper.valueToTree(value);

    assertEquals(expected, written);
    assertEquals(value, mapper.readValue(mapper.writeValueAsString(value), type));
  }

  @Test
  void parsesTheSampleAgentCardAsPublished() throws IOException {
    // The published sample writes "security", which v1.0 does not define; it is ignored.
    String json =
        """
        {"name": "n", "security": [{"google": ["openid"]}],
         "skills": [{"id": "s", "name": "s", "description": "d", "tags": ["t"]}]}
        """;
    A2aAgentCard card = mapper.readValue(json, A2aAgentCard.class);
    assertEquals(1, card.skills().size());
    assertTrue(card.securityRequirements().isEmpty());
  }

  @Test
  void jsonRpcParamsParseAsTheRequestTypeOfTheirMethod() throws IOException {
    JsonRpcRequest get = rpc("9.4.3-get-task-request.JsonRpcRequest.json");
    assertEquals(A2aMethods.GET_TASK, get.method());
    A2aGetTaskRequest getTask = mapper.treeToValue(get.params(), A2aGetTaskRequest.class);
    assertEquals(new A2aGetTaskRequest(null, "task-uuid", 10), getTask);

    JsonRpcRequest list = rpc("9.4.4-list-tasks-request.JsonRpcRequest.json");
    A2aListTasksRequest listTasks = mapper.treeToValue(list.params(), A2aListTasksRequest.class);
    assertEquals(A2aTaskState.WORKING, listTasks.status());
    assertEquals("context-uuid", listTasks.contextId());
    assertEquals(50, listTasks.pageSize());
    assertEquals("cursor-token", listTasks.pageToken());

    JsonRpcRequest cancel = rpc("9.4.5-cancel-task-request.JsonRpcRequest.json");
    assertEquals(
        new A2aCancelTaskRequest(null, "task-uuid", null),
        mapper.treeToValue(cancel.params(), A2aCancelTaskRequest.class));

    JsonRpcRequest subscribe = rpc("9.4.6-subscribe-to-task-request.JsonRpcRequest.json");
    assertEquals(
        new A2aSubscribeToTaskRequest(null, "task-uuid"),
        mapper.treeToValue(subscribe.params(), A2aSubscribeToTaskRequest.class));

    JsonRpcRequest extended = rpc("9.4.8-get-extended-agent-card-request.JsonRpcRequest.json");
    assertEquals(A2aMethods.GET_EXTENDED_AGENT_CARD, extended.method());
    assertEquals(null, extended.params());

    JsonRpcRequest send = rpc("9.4.1-send-message-request.JsonRpcRequest.json");
    A2aSendMessageRequest sendMessage =
        mapper.treeToValue(send.params(), A2aSendMessageRequest.class);
    assertEquals("What is the weather today?", sendMessage.message().parts().get(0).text());

    JsonRpcResponse response =
        exampleReader.readValue(
            Files.readString(example("9.4.2-stream-event.JsonRpcResponse.json")),
            JsonRpcResponse.class);
    A2aStreamResponse stream = mapper.treeToValue(response.result(), A2aStreamResponse.class);
    assertEquals(A2aTaskState.COMPLETED, stream.statusUpdate().status().state());
  }

  private static Path example(String name) {
    try {
      return Paths.get(A2aTypesJsonTest.class.getResource("/a2a/" + name).toURI());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static JsonRpcRequest rpc(String name) throws IOException {
    return exampleReader.readValue(Files.readString(example(name)), JsonRpcRequest.class);
  }

  // enums

  @Test
  void taskStatesUseTheProtoJsonNames() throws IOException {
    Map<A2aTaskState, String> expected =
        Map.of(
            A2aTaskState.SUBMITTED, "TASK_STATE_SUBMITTED",
            A2aTaskState.WORKING, "TASK_STATE_WORKING",
            A2aTaskState.COMPLETED, "TASK_STATE_COMPLETED",
            A2aTaskState.FAILED, "TASK_STATE_FAILED",
            A2aTaskState.CANCELED, "TASK_STATE_CANCELED",
            A2aTaskState.INPUT_REQUIRED, "TASK_STATE_INPUT_REQUIRED",
            A2aTaskState.REJECTED, "TASK_STATE_REJECTED",
            A2aTaskState.AUTH_REQUIRED, "TASK_STATE_AUTH_REQUIRED",
            A2aTaskState.UNSPECIFIED, "TASK_STATE_UNSPECIFIED");
    assertEquals(A2aTaskState.values().length, expected.size());
    for (var entry : expected.entrySet()) {
      assertEquals("\"" + entry.getValue() + "\"", mapper.writeValueAsString(entry.getKey()));
      assertEquals(
          entry.getKey(), mapper.readValue("\"" + entry.getValue() + "\"", A2aTaskState.class));
    }
  }

  @Test
  void rolesUseTheProtoJsonNames() throws IOException {
    assertEquals("\"ROLE_USER\"", mapper.writeValueAsString(A2aRole.USER));
    assertEquals("\"ROLE_AGENT\"", mapper.writeValueAsString(A2aRole.AGENT));
    assertEquals(A2aRole.AGENT, mapper.readValue("\"ROLE_AGENT\"", A2aRole.class));
  }

  @Test
  void rejectsEnumNamesThatAreNotProtocolNames() {
    // the error example in section 6.5 uses TASK_STATE_RUNNING, which is not a state
    assertThrows(
        JsonProcessingException.class,
        () -> mapper.readValue("\"TASK_STATE_RUNNING\"", A2aTaskState.class));
    assertThrows(
        JsonProcessingException.class, () -> mapper.readValue("\"completed\"", A2aTaskState.class));
    assertThrows(JsonProcessingException.class, () -> mapper.readValue("\"USER\"", A2aRole.class));
  }

  @Test
  void onlyCompletedFailedCanceledAndRejectedAreTerminal() {
    for (A2aTaskState state : A2aTaskState.values()) {
      boolean expected =
          state == A2aTaskState.COMPLETED
              || state == A2aTaskState.FAILED
              || state == A2aTaskState.CANCELED
              || state == A2aTaskState.REJECTED;
      assertEquals(expected, state.terminal(), state.name());
    }
  }

  // unknown fields

  @Test
  void ignoresUnknownFieldsAtEveryLevel() throws IOException {
    String json =
        """
        {"task": {"id": "t1", "future": 1,
           "status": {"state": "TASK_STATE_WORKING", "future": {"a": [1]},
             "message": {"messageId": "m", "role": "ROLE_AGENT", "kind": "message",
               "parts": [{"text": "hi", "kind": "text", "future": true}]}},
           "artifacts": [{"artifactId": "a", "future": "x", "parts": [{"data": {"k": 1}}]}]},
         "future": "top"}
        """;
    A2aSendMessageResponse response = mapper.readValue(json, A2aSendMessageResponse.class);
    A2aTask task = response.task();
    assertEquals("t1", task.id());
    assertEquals("hi", task.status().message().parts().get(0).text());
    assertEquals(Map.of("k", 1), task.artifacts().get(0).parts().get(0).data());
  }

  @Test
  void ignoresUnknownFieldsOnTheEnvelopeAndTheCard() throws IOException {
    JsonRpcRequest request =
        mapper.readValue(
            "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"GetTask\",\"x\":1}",
            JsonRpcRequest.class);
    assertTrue(request.valid());
    A2aAgentCard card =
        mapper.readValue(
            "{\"name\":\"n\",\"capabilities\":{\"streaming\":true,\"x\":1},\"x\":[]}",
            A2aAgentCard.class);
    assertEquals(Boolean.TRUE, card.capabilities().streaming());
  }

  // parts

  @Test
  void aPartHoldsExactlyOneContentMember() {
    assertRejected("{}");
    assertRejected("{\"filename\": \"a.txt\"}");
    assertRejected("{\"text\": \"a\", \"url\": \"https://example.com\"}");
    assertRejected("{\"text\": \"a\", \"raw\": \"AAAA\"}");
    assertRejected("{\"raw\": \"AAAA\", \"data\": {\"a\": 1}}");
    assertRejected("{\"url\": \"https://example.com\", \"data\": 1}");
  }

  private static void assertRejected(String partJson) {
    assertThrows(JsonProcessingException.class, () -> mapper.readValue(partJson, A2aPart.class));
    assertThrows(
        JsonProcessingException.class,
        () -> mapper.readValue("{\"parts\": [" + partJson + "]}", A2aMessage.class));
  }

  @Test
  void aRawPartMustBeBase64() {
    assertRejected("{\"raw\": \"not base64!\"}");
    assertThrows(
        IllegalArgumentException.class,
        () -> new A2aPart(null, "%%%", null, null, null, null, null));
  }

  @Test
  void aTextPartKeepsAnEmptyString() throws IOException {
    A2aPart part = mapper.readValue("{\"text\": \"\"}", A2aPart.class);
    assertEquals("", part.text());
    assertEquals("{\"text\":\"\"}", mapper.writeValueAsString(part));
  }

  @Test
  void aDataPartHoldsAnyJsonValue() throws IOException {
    for (String value :
        List.of("1", "1.5", "true", "false", "\"s\"", "[1,\"a\",null]", "{\"a\":{\"b\":[]}}")) {
      String json = "{\"data\":" + value + "}";
      A2aPart part = mapper.readValue(json, A2aPart.class);
      assertEquals(mapper.readTree(json), mapper.valueToTree(part), json);
    }
  }

  @Test
  void rawBytesDecodeBothBase64Alphabets() throws IOException {
    byte[] bytes = {(byte) 0xfb, (byte) 0xff, (byte) 0xfe, 1, 2};
    A2aPart standard = A2aPart.raw(bytes, "application/octet-stream", "a.bin");
    assertArrayEquals(bytes, standard.rawBytes());
    A2aPart urlSafe = mapper.readValue("{\"raw\": \"-__-AQI=\"}", A2aPart.class);
    assertArrayEquals(bytes, urlSafe.rawBytes());
    assertEquals(
        standard.raw(), mapper.readValue(mapper.writeValueAsString(standard), A2aPart.class).raw());
  }

  // unions

  @Test
  void responsesHoldExactlyOneMember() {
    assertThrows(
        JsonProcessingException.class, () -> mapper.readValue("{}", A2aStreamResponse.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            mapper.readValue(
                "{\"task\": {\"id\": \"1\"}, \"message\": {\"messageId\": \"2\"}}",
                A2aStreamResponse.class));
    assertThrows(
        JsonProcessingException.class, () -> mapper.readValue("{}", A2aSendMessageResponse.class));
    assertThrows(
        JsonProcessingException.class, () -> mapper.readValue("{}", A2aSecurityScheme.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            mapper.readValue(
                "{\"implicit\": {}, \"password\": {}}", A2aSecurityScheme.OAuthFlows.class));
  }

  @Test
  void aStreamResponseCarriesAnyTaskUpdate() throws IOException {
    var status =
        new A2aTaskStatusUpdate(
            "t", "c", new A2aTaskStatus(A2aTaskState.WORKING, null, null), null);
    var artifact =
        new A2aTaskArtifactUpdate(
            "t",
            "c",
            new A2aArtifact("a", null, null, List.of(A2aPart.text("x")), null, null),
            true,
            true,
            null);
    for (A2aTaskUpdate update : List.<A2aTaskUpdate>of(status, artifact)) {
      A2aStreamResponse response = A2aStreamResponse.of(update);
      assertEquals(
          response, mapper.readValue(mapper.writeValueAsString(response), A2aStreamResponse.class));
    }
    JsonNode tree = mapper.valueToTree(A2aStreamResponse.of(artifact));
    assertEquals(true, tree.at("/artifactUpdate/append").asBoolean());
    assertEquals(true, tree.at("/artifactUpdate/lastChunk").asBoolean());
  }

  // field presence

  @Test
  void leavesOutDefaultValuesAsProtoJsonDoes() throws IOException {
    var update =
        new A2aTaskArtifactUpdate(
            "t", "c", new A2aArtifact("a", null, null, null, null, null), false, false, null);
    ObjectNode tree = mapper.valueToTree(update);
    assertFalse(tree.has("append"));
    assertFalse(tree.has("lastChunk"));
    assertFalse(tree.has("metadata"));
    assertFalse(tree.at("/artifact").has("parts"));
    assertEquals(
        "{\"streaming\":false}",
        mapper.writeValueAsString(new A2aAgentCard.Capabilities(false, null, null, null)));
  }

  @Test
  void listTasksResponseAlwaysWritesItsRequiredMembers() throws IOException {
    JsonNode tree = mapper.valueToTree(new A2aListTasksResponse(null, null, 0, 0));
    assertEquals(
        mapper.readTree("{\"tasks\":[],\"nextPageToken\":\"\",\"pageSize\":0,\"totalSize\":0}"),
        tree);
  }

  @Test
  void listTasksRequestKeepsItsFilter() throws IOException {
    var request =
        new A2aListTasksRequest(
            null,
            "ctx",
            A2aTaskState.INPUT_REQUIRED,
            20,
            "tok",
            0,
            Instant.parse("2025-10-28T10:30:00.000Z"),
            false);
    JsonNode tree = mapper.valueToTree(request);
    assertEquals("TASK_STATE_INPUT_REQUIRED", tree.get("status").asText());
    assertEquals("2025-10-28T10:30:00Z", tree.get("statusTimestampAfter").asText());
    assertEquals(0, tree.get("historyLength").asInt());
    assertFalse(tree.get("includeArtifacts").asBoolean());
    assertEquals(request, mapper.treeToValue(tree, A2aListTasksRequest.class));
  }

  @Test
  void timestampsAcceptWholeSecondsMillisecondsAndMicroseconds() throws IOException {
    for (String text :
        List.of(
            "2024-03-15T10:15:00Z", "2025-10-28T10:30:00.000Z", "2025-04-17T17:47:09.680794Z")) {
      A2aTaskStatus status =
          mapper.readValue("{\"timestamp\":\"" + text + "\"}", A2aTaskStatus.class);
      assertEquals(Instant.parse(text), status.timestamp());
    }
  }

  // immutability

  @Test
  void recordsCopyTheirCollections() {
    var parts = new java.util.ArrayList<>(List.of(A2aPart.text("a")));
    var metadata = new java.util.HashMap<String, Object>(Map.of("k", "v"));
    A2aMessage message = new A2aMessage("m", null, null, A2aRole.USER, parts, metadata, null, null);
    parts.add(A2aPart.text("b"));
    metadata.put("k2", "v2");
    assertEquals(1, message.parts().size());
    assertEquals(1, message.metadata().size());
    assertThrows(UnsupportedOperationException.class, () -> message.parts().add(A2aPart.text("c")));
    assertThrows(UnsupportedOperationException.class, () -> message.metadata().put("x", "y"));
    assertTrue(message.extensions().isEmpty());
  }

  // JSON-RPC

  @Test
  void jsonRpcErrorCodesMatchTheSpecification() {
    Map<A2aErrorCode, Integer> expected =
        Map.ofEntries(
            Map.entry(A2aErrorCode.JSON_PARSE_ERROR, -32700),
            Map.entry(A2aErrorCode.INVALID_REQUEST, -32600),
            Map.entry(A2aErrorCode.METHOD_NOT_FOUND, -32601),
            Map.entry(A2aErrorCode.INVALID_PARAMS, -32602),
            Map.entry(A2aErrorCode.INTERNAL_ERROR, -32603),
            Map.entry(A2aErrorCode.TASK_NOT_FOUND, -32001),
            Map.entry(A2aErrorCode.TASK_NOT_CANCELABLE, -32002),
            Map.entry(A2aErrorCode.PUSH_NOTIFICATION_NOT_SUPPORTED, -32003),
            Map.entry(A2aErrorCode.UNSUPPORTED_OPERATION, -32004),
            Map.entry(A2aErrorCode.CONTENT_TYPE_NOT_SUPPORTED, -32005),
            Map.entry(A2aErrorCode.INVALID_AGENT_RESPONSE, -32006),
            Map.entry(A2aErrorCode.EXTENDED_AGENT_CARD_NOT_CONFIGURED, -32007),
            Map.entry(A2aErrorCode.EXTENSION_SUPPORT_REQUIRED, -32008),
            Map.entry(A2aErrorCode.VERSION_NOT_SUPPORTED, -32009));
    assertEquals(A2aErrorCode.values().length, expected.size());
    for (var entry : expected.entrySet()) {
      assertEquals(entry.getValue(), entry.getKey().code());
      assertEquals(entry.getKey(), A2aErrorCode.fromCode(entry.getValue()).orElseThrow());
      assertEquals(
          entry.getKey().code() >= -32009 && entry.getKey().code() <= -32001,
          entry.getKey().a2aSpecific());
    }
    assertTrue(A2aErrorCode.fromCode(-32050).isEmpty());
    assertEquals("TASK_NOT_FOUND", A2aErrorCode.TASK_NOT_FOUND.reason());
  }

  @Test
  void jsonRpcResponseWritesANullIdAndOnlyOneOfResultAndError() throws IOException {
    JsonRpcResponse failure =
        JsonRpcResponse.failure(null, JsonRpcError.of(A2aErrorCode.JSON_PARSE_ERROR));
    JsonNode tree = mapper.valueToTree(failure);
    assertTrue(tree.get("id").isNull());
    assertFalse(tree.has("result"));
    assertEquals(-32700, tree.at("/error/code").asInt());
    assertFalse(tree.at("/error").has("data"));

    JsonNode ok =
        mapper.valueToTree(
            JsonRpcResponse.success(
                mapper.getNodeFactory().numberNode(7), mapper.createObjectNode()));
    assertEquals(7, ok.get("id").asInt());
    assertFalse(ok.has("error"));
    assertTrue(ok.get("result").isObject());
  }

  @Test
  void jsonRpcRequestValidation() throws IOException {
    assertTrue(
        mapper
            .readValue("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"m\"}", JsonRpcRequest.class)
            .valid());
    assertTrue(
        mapper
            .readValue("{\"jsonrpc\":\"2.0\",\"id\":\"x\",\"method\":\"m\"}", JsonRpcRequest.class)
            .valid());
    assertTrue(
        mapper
            .readValue("{\"jsonrpc\":\"2.0\",\"method\":\"notification\"}", JsonRpcRequest.class)
            .valid());
    assertFalse(
        mapper
            .readValue("{\"jsonrpc\":\"1.0\",\"id\":1,\"method\":\"m\"}", JsonRpcRequest.class)
            .valid());
    assertFalse(mapper.readValue("{\"jsonrpc\":\"2.0\",\"id\":1}", JsonRpcRequest.class).valid());
    assertFalse(
        mapper
            .readValue("{\"jsonrpc\":\"2.0\",\"id\":{},\"method\":\"m\"}", JsonRpcRequest.class)
            .valid());
  }

  @Test
  void methodNamesMatchTheSpecification() {
    assertEquals("SendMessage", A2aMethods.SEND_MESSAGE);
    assertEquals("SendStreamingMessage", A2aMethods.SEND_STREAMING_MESSAGE);
    assertEquals("SubscribeToTask", A2aMethods.SUBSCRIBE_TO_TASK);
    assertEquals(
        "CreateTaskPushNotificationConfig", A2aMethods.CREATE_TASK_PUSH_NOTIFICATION_CONFIG);
    assertEquals("ListTaskPushNotificationConfigs", A2aMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIGS);
  }
}
