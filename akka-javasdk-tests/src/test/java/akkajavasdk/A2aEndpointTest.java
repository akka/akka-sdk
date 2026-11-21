/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.JsonSupport;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.http.StrictResponse;
import akka.javasdk.impl.a2a.AgentCardSigning;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.util.ByteString;
import akkajavasdk.components.a2a.A2aAssistantAgent;
import akkajavasdk.components.a2a.A2aEchoAgent;
import akkajavasdk.components.a2a.TestA2aEndpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class A2aEndpointTest extends TestKitSupport {

  // A2A endpoint defined in akkajavasdk.components.a2a.TestA2aEndpoint

  private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

  private final TestModelProvider echoModel = new TestModelProvider();
  private final TestModelProvider assistantModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    echoModel.fixedResponse(
        input -> {
          var text = input.content();
          if (text.equals("fail")) throw new RuntimeException("model unavailable");
          return new AiResponse("echo: " + text);
        });
    assistantModel.fixedResponse("Noted.");
    var signingKey =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder().encodeToString(CARD_KEYS.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    return TestKit.Settings.DEFAULT
        .withModelProvider(A2aEchoAgent.class, echoModel)
        .withModelProvider(A2aAssistantAgent.class, assistantModel)
        .withAdditionalConfig(
            ConfigFactory.empty()
                .withValue(
                    "akka.javasdk.a2a.endpoint.signing.key",
                    ConfigValueFactory.fromAnyRef(signingKey))
                .withValue(
                    "akka.javasdk.a2a.endpoint.signing.key-id",
                    ConfigValueFactory.fromAnyRef("card-key")));
  }

  // The key that signs the Agent Card of the test service
  private static final KeyPair CARD_KEYS;

  static {
    try {
      var generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp256r1"));
      CARD_KEYS = generator.generateKeyPair();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  public void signsTheAgentCardWithTheConfiguredKey() throws Exception {
    var card = json(httpClient.GET("/.well-known/agent-card.json").invoke().body());
    var signature = card.get("signatures").get(0);
    var header =
        json(
            ByteString.fromArray(
                Base64.getUrlDecoder().decode(signature.get("protected").asText())));
    assertThat(header.get("kid").asText()).isEqualTo("card-key");
    assertThat(header.get("alg").asText()).isEqualTo("ES256");
    var verification =
        AgentCardSigning.verify(
            card, AgentCardSigning.KeyResolver.forKey("card-key", CARD_KEYS.getPublic()));
    assertThat(verification.verified()).isTrue();
  }

  @Test
  public void answersAnAgentCardRequestWithTheCurrentETagWithNotModified() throws Exception {
    var first = httpClient.GET("/.well-known/agent-card.json").invoke();
    var etag = first.httpResponse().getHeader("ETag").get().value();
    assertThat(etag).startsWith("W/\"").endsWith("\"");

    var again =
        httpClient.GET("/.well-known/agent-card.json").addHeader("If-None-Match", etag).invoke();
    assertThat(again.status()).isEqualTo(StatusCodes.NOT_MODIFIED);
    assertThat(again.httpResponse().getHeader("ETag").get().value()).isEqualTo(etag);

    var changed =
        httpClient
            .GET("/.well-known/agent-card.json")
            .addHeader("If-None-Match", "\"something-else\"")
            .invoke();
    assertThat(changed.status()).isEqualTo(StatusCodes.OK);
  }

  @Test
  public void givesTheEndpointTheHeadersOfTheRequest() throws Exception {
    var response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token("tagged"))
            .addHeader("X-Test-Tag", "tag-1")
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(rpc("SendMessage", sendParams("echo", "with a tag", null))))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(TestA2aEndpoint.lastTag).isEqualTo("tag-1");
  }

  @Test
  public void servesAgentCardAtRootWithoutToken() throws Exception {
    var response = httpClient.GET("/.well-known/agent-card.json").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    assertThat(response.httpResponse().getHeader("Cache-Control").get().value())
        .isEqualTo("max-age=300");

    var card = json(response.body());
    assertThat(card.get("name").asText()).isEqualTo("SDK test agents");
    assertThat(card.get("description").asText()).isEqualTo("Agents of the SDK integration tests");
    assertThat(card.get("version").asText()).isEqualTo("1.0.0");

    var interfaces = card.get("supportedInterfaces");
    assertThat(interfaces).hasSize(2);
    assertThat(interfaces.get(0).get("protocolBinding").asText()).isEqualTo("JSONRPC");
    assertThat(interfaces.get(1).get("protocolBinding").asText()).isEqualTo("HTTP+JSON");
    for (JsonNode i : interfaces) {
      assertThat(i.get("url").asText()).startsWith("http://localhost:").endsWith("/a2a");
      assertThat(i.get("protocolVersion").asText()).isEqualTo("1.0");
    }

    assertThat(card.get("capabilities").get("streaming").asBoolean()).isTrue();
    assertThat(card.get("defaultInputModes").toString()).contains("*/*");

    var skills = card.get("skills");
    assertThat(skills).hasSize(5);
    var echo = skills.get(0);
    assertThat(echo.get("id").asText()).isEqualTo("echo");
    assertThat(echo.get("name").asText()).isEqualTo("Echo");
    assertThat(echo.get("description").asText()).isEqualTo("Repeats the text it receives");
    assertThat(echo.get("tags").get(0).asText()).isEqualTo("echo");
    assertThat(echo.get("examples").get(0).asText()).isEqualTo("hello");
    var assistant = skills.get(1);
    assertThat(assistant.get("id").asText()).isEqualTo("a2a-assistant");
    assertThat(assistant.get("name").asText()).isEqualTo("Assistant");
    assertThat(assistant.get("description").asText())
        .isEqualTo("Answers questions and remembers the conversation");

    var schemes = card.get("securitySchemes");
    assertThat(
            schemes.get("oidc").get("openIdConnectSecurityScheme").get("openIdConnectUrl").asText())
        .isEqualTo("https://issuer.a2a.example/.well-known/openid-configuration");
    assertThat(schemes.get("bearer").get("httpAuthSecurityScheme").get("scheme").asText())
        .isEqualTo("Bearer");
    assertThat(card.get("securityRequirements")).hasSize(2);
  }

  @Test
  public void sendsMessageOverJsonRpc() throws Exception {
    var result =
        jsonRpc("SendMessage", sendParams("echo", "hello world", null), token("alice"))
            .get("result");

    var task = result.get("task");
    assertThat(task.get("id").asText()).isNotBlank();
    assertThat(task.get("contextId").asText()).isNotBlank();
    assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(task.get("status").get("timestamp").asText()).endsWith("Z");
    assertThat(task.get("artifacts").get(0).get("parts").get(0).get("text").asText())
        .isEqualTo("echo: hello world");
    var history = task.get("history");
    assertThat(history).hasSize(2);
    assertThat(history.get(0).get("role").asText()).isEqualTo("ROLE_USER");
    assertThat(history.get(1).get("role").asText()).isEqualTo("ROLE_AGENT");
    assertThat(TestA2aEndpoint.lastSubject).isEqualTo("alice");
  }

  @Test
  public void sendsMessageOverHttpJson() throws Exception {
    var response =
        httpClient
            .POST("/a2a/message:send")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, bytes(sendParams("echo", "over rest", null)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    var task = json(response.body()).get("task");
    assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(task.get("artifacts").get(0).get("parts").get(0).get("text").asText())
        .isEqualTo("echo: over rest");
  }

  @Test
  public void twoTurnsInOneContextShareSessionMemory() throws Exception {
    var contextId = UUID.randomUUID().toString();
    var first =
        jsonRpc("SendMessage", sendParams("a2a-assistant", "My name is Ada", contextId), token("a"))
            .get("result")
            .get("task");
    var second =
        jsonRpc(
                "SendMessage",
                sendParams("a2a-assistant", "What is my name?", contextId),
                token("a"))
            .get("result")
            .get("task");

    assertThat(first.get("contextId").asText()).isEqualTo(contextId);
    assertThat(second.get("contextId").asText()).isEqualTo(contextId);
    assertThat(second.get("id").asText()).isNotEqualTo(first.get("id").asText());

    var userMessages =
        componentClient
            .forEventSourcedEntity(contextId)
            .method(SessionMemoryEntity::getHistory)
            .invoke(new SessionMemoryEntity.GetHistoryCmd())
            .messages()
            .stream()
            .filter(m -> m instanceof SessionMessage.UserMessage)
            .map(m -> ((SessionMessage.UserMessage) m).text())
            .toList();
    assertThat(userMessages).containsExactly("My name is Ada", "What is my name?");
  }

  @Test
  public void getsTaskOverBothBindings() throws Exception {
    var token = token("bob");
    var sent =
        jsonRpc("SendMessage", sendParams("echo", "remember me", null), token)
            .get("result")
            .get("task");
    var taskId = sent.get("id").asText();

    var viaJsonRpc =
        jsonRpc("GetTask", Map.of("id", taskId, "historyLength", 1), token).get("result");
    assertThat(viaJsonRpc.get("status").get("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(viaJsonRpc.get("history")).hasSize(1);
    assertThat(viaJsonRpc.get("history").get(0).get("role").asText()).isEqualTo("ROLE_AGENT");

    var viaHttp = httpClient.GET("/a2a/tasks/" + taskId).addHeader("Authorization", token).invoke();
    assertThat(viaHttp.status()).isEqualTo(StatusCodes.OK);
    var task = json(viaHttp.body());
    assertThat(task.get("id").asText()).isEqualTo(taskId);
    assertThat(task.get("artifacts").get(0).get("parts").get(0).get("text").asText())
        .isEqualTo("echo: remember me");

    // a task of another subject is not revealed
    var otherSubject =
        jsonRpc("GetTask", Map.of("id", taskId), token("mallory")).get("error").get("code");
    assertThat(otherSubject.asInt()).isEqualTo(-32001);
  }

  @Test
  public void answersTaskNotFoundOnBothBindings() throws Exception {
    var error = jsonRpc("GetTask", Map.of("id", "no-such-task"), token("a")).get("error");
    assertThat(error.get("code").asInt()).isEqualTo(-32001);
    assertThat(error.get("data").get(0).get("reason").asText()).isEqualTo("TASK_NOT_FOUND");

    var response =
        httpClient.GET("/a2a/tasks/no-such-task").addHeader("Authorization", token("a")).invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
    assertThat(response.httpResponse().entity().getContentType().toString())
        .startsWith("application/json");
    var body = json(response.body()).get("error");
    assertThat(body.get("code").asInt()).isEqualTo(404);
    assertThat(body.get("status").asText()).isEqualTo("NOT_FOUND");
    var detail = body.get("details").get(0);
    assertThat(detail.get("@type").asText()).isEqualTo("type.googleapis.com/google.rpc.ErrorInfo");
    assertThat(detail.get("reason").asText()).isEqualTo("TASK_NOT_FOUND");
    assertThat(detail.get("domain").asText()).isEqualTo("a2a-protocol.org");
  }

  @Test
  public void rejectsUnknownSkillAndDefaultsToTheFirstSkill() throws Exception {
    var unknown = jsonRpc("SendMessage", sendParams("nope", "hi", null), token("a")).get("error");
    assertThat(unknown.get("code").asInt()).isEqualTo(-32602);
    assertThat(unknown.get("message").asText()).contains("nope").contains("echo, a2a-assistant");

    var missing =
        jsonRpc("SendMessage", sendParams(null, "hi", null), token("a")).get("result").get("task");
    assertThat(missing.get("artifacts").get(0).get("parts").get(0).get("text").asText())
        .isEqualTo("echo: hi");
  }

  @Test
  public void rejectsMessageToEndedTask() throws Exception {
    var token = token("a");
    var task =
        jsonRpc("SendMessage", sendParams("echo", "once", null), token).get("result").get("task");
    var params = sendParams("echo", "twice", task.get("contextId").asText());
    @SuppressWarnings("unchecked")
    var message = (Map<String, Object>) params.get("message");
    message.put("taskId", task.get("id").asText());
    var error = jsonRpc("SendMessage", params, token).get("error");
    assertThat(error.get("code").asInt()).isEqualTo(-32004);
  }

  @Test
  public void recordsFailedTaskWhenAgentFails() throws Exception {
    var task =
        jsonRpc("SendMessage", sendParams("echo", "fail", null), token("a"))
            .get("result")
            .get("task");
    assertThat(task.get("status").get("state").asText()).isEqualTo("TASK_STATE_FAILED");
    assertThat(task.get("status").get("message").get("role").asText()).isEqualTo("ROLE_AGENT");
  }

  @Test
  public void answersUnsupportedMethodsAndVersions() throws Exception {
    var unknown = jsonRpc("NoSuchMethod", Map.of(), token("a")).get("error");
    assertThat(unknown.get("code").asInt()).isEqualTo(-32601);

    var version =
        json(httpClient
                .POST("/a2a")
                .addHeader("Authorization", token("a"))
                .addHeader("A2A-Version", "0.3")
                .withRequestBody(
                    ContentTypes.APPLICATION_JSON,
                    bytes(rpc("SendMessage", sendParams("echo", "hi", null))))
                .invoke()
                .body())
            .get("error");
    assertThat(version.get("code").asInt()).isEqualTo(-32009);

    var patchVersion =
        json(
            httpClient
                .POST("/a2a")
                .addHeader("Authorization", token("a"))
                .addHeader("A2A-Version", "1.0.2")
                .withRequestBody(
                    ContentTypes.APPLICATION_JSON,
                    bytes(rpc("SendMessage", sendParams("echo", "hi", null))))
                .invoke()
                .body());
    assertThat(patchVersion.has("result")).isTrue();

    var parseError =
        json(httpClient
                .POST("/a2a")
                .addHeader("Authorization", token("a"))
                .withRequestBody(
                    ContentTypes.APPLICATION_JSON, "{not json".getBytes(StandardCharsets.UTF_8))
                .invoke()
                .body())
            .get("error");
    assertThat(parseError.get("code").asInt()).isEqualTo(-32700);
  }

  @Test
  public void refusesRequestsWithoutToken() {
    var response =
        httpClient
            .POST("/a2a")
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(rpc("SendMessage", sendParams("echo", "hi", null))))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);

    var rest = httpClient.GET("/a2a/tasks/any").invoke();
    assertThat(rest.status()).isEqualTo(StatusCodes.BAD_REQUEST);

    var header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims =
        Base64.getEncoder().encodeToString(bytes(Map.of("iss", "other-issuer", "sub", "a")));
    var wrongIssuer =
        httpClient
            .GET("/a2a/tasks/any")
            .addHeader("Authorization", "Bearer " + header + "." + claims)
            .invoke();
    assertThat(wrongIssuer.status().intValue()).isBetween(400, 403);
  }

  @Test
  public void refusesCallersThatTheAclDenies() {
    var card =
        httpClient
            .GET("/.well-known/agent-card.json")
            .addHeader("impersonate-service", "blocked-service")
            .invoke();
    assertThat(card.status()).isEqualTo(StatusCodes.FORBIDDEN);

    var send =
        httpClient
            .POST("/a2a")
            .addHeader("impersonate-service", "blocked-service")
            .addHeader("Authorization", token("a"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                bytes(rpc("SendMessage", sendParams("echo", "hi", null))))
            .invoke();
    assertThat(send.status()).isEqualTo(StatusCodes.FORBIDDEN);
  }

  private JsonNode jsonRpc(String method, Object params, String token) throws Exception {
    StrictResponse<ByteString> response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token)
            .addHeader("A2A-Version", "1.0")
            .withRequestBody(ContentTypes.APPLICATION_JSON, bytes(rpc(method, params)))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    return json(response.body());
  }

  private static Map<String, Object> rpc(String method, Object params) {
    return Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params);
  }

  private static Map<String, Object> sendParams(String skillId, String text, String contextId) {
    var message = new java.util.LinkedHashMap<String, Object>();
    message.put("messageId", UUID.randomUUID().toString());
    message.put("role", "ROLE_USER");
    message.put("parts", java.util.List.of(Map.of("text", text)));
    if (skillId != null) message.put("metadata", Map.of("skillId", skillId));
    if (contextId != null) message.put("contextId", contextId);
    var params = new java.util.LinkedHashMap<String, Object>();
    params.put("message", message);
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

  // A token without signature, which the runtime accepts in dev mode and in tests
  private static String token(String subject) {
    var header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims =
        Base64.getEncoder().encodeToString(bytes(Map.of("iss", "a2a-test-issuer", "sub", subject)));
    return "Bearer " + header + "." + claims;
  }
}
