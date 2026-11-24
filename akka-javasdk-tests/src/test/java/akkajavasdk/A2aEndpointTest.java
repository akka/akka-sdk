/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class A2aEndpointTest extends TestKitSupport {

  // A2A endpoints defined in akkajavasdk.components.a2a

  private static final ObjectMapper mapper = new ObjectMapper();

  private JsonNode sendMessage(String path, String text, String contextId) {
    var contextField = contextId == null ? "" : ",\"contextId\":\"" + contextId + "\"";
    var body =
        "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SendMessage\",\"params\":{\"message\":"
            + "{\"messageId\":\"m1\",\"role\":\"ROLE_USER\",\"parts\":[{\"text\":\""
            + text
            + "\"}]"
            + contextField
            + "}}}";
    var response =
        httpClient
            .POST(path)
            .addHeader("A2A-Version", "1.0")
            .withRequestBody(ContentTypes.APPLICATION_JSON, body.getBytes(StandardCharsets.UTF_8))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    try {
      return mapper.readTree(response.body().utf8String());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static String replyText(JsonNode response) {
    return response.get("result").get("message").get("parts").get(0).get("text").asText();
  }

  @Test
  public void serveAgentCard() throws Exception {
    var response = httpClient.GET("/a2a/test/.well-known/agent-card.json").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);

    var card = mapper.readTree(response.body().utf8String());
    assertThat(card.get("name").asText()).isEqualTo("Test agent");
    assertThat(card.get("description").asText()).isEqualTo("An agent for the tests");
    assertThat(card.get("version").asText()).isEqualTo("1.2.3");
    assertThat(card.get("provider").get("organization").asText()).isEqualTo("Example Inc");
    var skill = card.get("skills").get(0);
    assertThat(skill.get("id").asText()).isEqualTo("echo");
    assertThat(skill.get("tags").get(0).asText()).isEqualTo("test");
    var iface = card.get("supportedInterfaces").get(0);
    assertThat(iface.get("protocolBinding").asText()).isEqualTo("JSONRPC");
    assertThat(iface.get("protocolVersion").asText()).isEqualTo("1.0");
    assertThat(iface.get("url").asText()).endsWith("/a2a/test");
  }

  @Test
  public void serveCardOfRootEndpointAtWellKnownPath() throws Exception {
    var response = httpClient.GET("/.well-known/agent-card.json").invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.OK);
    var card = mapper.readTree(response.body().utf8String());
    assertThat(card.get("name").asText()).isEqualTo("Root agent");
    assertThat(card.has("provider")).isFalse();
  }

  @Test
  public void answerMessageAtRootEndpoint() {
    assertThat(replyText(sendMessage("/", "hello", null))).isEqualTo("root: hello");
  }

  @Test
  public void answerMessageWithAMessage() {
    var response = sendMessage("/a2a/test", "hello", "ctx-1");
    var message = response.get("result").get("message");
    assertThat(message.get("role").asText()).isEqualTo("ROLE_AGENT");
    assertThat(message.get("contextId").asText()).isEqualTo("ctx-1");
    assertThat(replyText(response)).startsWith("echo: hello|context=ctx-1|session=");
  }

  @Test
  public void generateContextIdForFirstMessage() {
    var response = sendMessage("/a2a/test", "hello", null);
    var contextId = response.get("result").get("message").get("contextId").asText();
    assertThat(contextId).isNotEmpty();
    assertThat(replyText(response)).contains("|context=" + contextId + "|");
  }

  @Test
  public void giveAnonymousCallerANewSessionForEachRequest() {
    var first = replyText(sendMessage("/a2a/test", "hello", "ctx-1"));
    var second = replyText(sendMessage("/a2a/test", "hello", "ctx-1"));
    assertThat(first).endsWith("|caller=none");
    assertThat(first.split("\\|session=")[1]).isNotEqualTo(second.split("\\|session=")[1]);
  }

  @Test
  public void hideFailureOfEndpoint() {
    var response = sendMessage("/a2a/test", "fail", null);
    assertThat(response.get("error").get("code").asInt()).isEqualTo(-32603);
    assertThat(response.get("error").get("message").asText()).doesNotContain("failed");
  }

  @Test
  public void rejectUnsupportedVersion() {
    var response =
        httpClient
            .POST("/a2a/test")
            .addHeader("A2A-Version", "0.3")
            .withRequestBody(
                ContentTypes.APPLICATION_JSON,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"SendMessage\"}"
                    .getBytes(StandardCharsets.UTF_8))
            .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
  }
}
