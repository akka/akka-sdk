/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aAgentSkill;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.ToolCallGuardrail;
import akka.javasdk.impl.a2a.A2aErrorCode;
import akka.javasdk.impl.a2a.JsonRpcError;
import akka.javasdk.impl.a2a.JsonRpcResponse;
import akka.javasdk.ledger.ToolCall;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.Junit5LogCapturing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * An agent offers the skills of a remote A2A agent to its model as tools. The remote agent is a
 * small A2A server in this test that answers {@code SendMessage} on the JSON-RPC binding.
 */
@ExtendWith(Junit5LogCapturing.class)
public class RemoteA2aToolIntegrationTest extends TestKitSupport {

  private static final FakeA2aAgent remote = FakeA2aAgent.start();

  private final TestModelProvider model = new TestModelProvider();
  private final ToolRecordingModelProvider recordingModel = new ToolRecordingModelProvider(model);

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(RemoteA2aToolTestAgent.class, recordingModel)
        .withModelProvider(RemoteA2aGuardedTestAgent.class, recordingModel)
        .withAdditionalConfig(
            """
            akka.javasdk.a2a.remote-agents.claims.card-url = "%s"
            akka.javasdk.agent.guardrails."remote a2a guard" {
              class = "akkajavasdk.components.agent.RemoteA2aDenyingToolGuard"
              agents = ["remote-a2a-guarded-test-agent"]
              category = TOOL_POLICY
            }
            """
                .formatted(remote.cardUrl()));
  }

  @AfterEach
  public void afterEach() {
    model.reset();
    recordingModel.offeredTools.clear();
    remote.requests.clear();
    RemoteA2aDenyingToolGuard.lastCall.set(null);
  }

  @AfterAll
  public static void stopRemote() {
    remote.server.stop(0);
  }

  @Test
  public void shouldCallTheRemoteSkillAndReplyWithItsResult() {
    model
        .whenMessage(message -> message.equals("Is claim 42 valid?"))
        .reply(
            new ToolInvocationRequest(
                "claims_check-claim", "{\"message\":\"check claim 42\",\"data\":{\"claim\":42}}"));
    model
        .whenToolResult(result -> result.name().equals("claims_check-claim"))
        .thenReply(result -> new AiResponse("Answer: " + result.content()));
    var sessionId = UUID.randomUUID().toString();

    var reply = ask(sessionId, "Is claim 42 valid?");

    assertThat(reply).isEqualTo("Answer: claim 42 is valid\n{\"valid\":true}");

    var params = remote.requests.peek();
    assertThat(params).isNotNull();
    assertThat(params.path("metadata").path("skillId").asText()).isEqualTo("check-claim");
    assertThat(params.path("message").path("contextId").asText()).isEqualTo(sessionId);
    assertThat(params.path("message").path("parts").get(0).path("text").asText())
        .isEqualTo("check claim 42");
    assertThat(params.path("message").path("parts").get(1).path("data").path("claim").asInt())
        .isEqualTo(42);
  }

  @Test
  public void shouldOfferOnlyTheAllowedSkills() {
    model.fixedResponse("no tools needed");

    ask(UUID.randomUUID().toString(), "hello");

    var tools = recordingModel.offeredTools.peek();
    assertThat(tools).extracting(ToolSpecification::name).containsExactly("claims_check-claim");
    assertThat(tools.getFirst().description()).contains("Checks whether a claim is valid");
    assertThat(tools.getFirst().parameters().required()).containsExactly("message");
  }

  @Test
  public void shouldRecordTheRemoteReplyInSessionMemoryAndTheLedger() {
    model
        .whenMessage(message -> message.equals("Is claim 7 valid?"))
        .reply(new ToolInvocationRequest("claims_check-claim", "{\"message\":\"check claim 7\"}"));
    model
        .whenToolResult(result -> result.name().equals("claims_check-claim"))
        .thenReply(result -> new AiResponse("done"));
    var sessionId = UUID.randomUUID().toString();

    ask(sessionId, "Is claim 7 valid?");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(storedHistory(sessionId))
                    .filteredOn(ToolCallResponse.class::isInstance)
                    .map(ToolCallResponse.class::cast)
                    .singleElement()
                    .satisfies(
                        response -> {
                          assertThat(response.name()).isEqualTo("claims_check-claim");
                          assertThat(response.text()).startsWith("claim 7 is valid");
                        }));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        getLedgerClient()
                            .getInteractions("remote-a2a-tool-test-agent", sessionId)
                            .getFirst()
                            .toolCalls("claims_check-claim"))
                    .singleElement()
                    .satisfies(
                        call -> {
                          assertThat(call.status()).isEqualTo(ToolCall.Status.COMPLETED);
                          assertThat(call.response()).startsWith("claim 7 is valid");
                        }));
  }

  @Test
  public void shouldReturnARemoteErrorToTheModel() {
    model
        .whenMessage(message -> message.equals("Is claim 99 valid?"))
        .reply(new ToolInvocationRequest("claims_check-claim", "{\"message\":\"fail\"}"));
    model
        .whenToolResult(result -> result.name().equals("claims_check-claim"))
        .thenReply(result -> new AiResponse("Seen: " + result.content()));

    var reply = ask(UUID.randomUUID().toString(), "Is claim 99 valid?");

    assertThat(reply).startsWith("Seen: Error: ").contains("no such claim");
  }

  @Test
  public void shouldLetAToolCallGuardrailDenyARemoteSkill() {
    model
        .whenMessage(message -> message.equals("Pay claim 42"))
        .reply(new ToolInvocationRequest("claims_pay", "{\"message\":\"pay claim 42\"}"));
    model
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse("Paid " + result.content()));

    var reply =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(RemoteA2aGuardedTestAgent::ask)
            .invoke("Pay claim 42");

    assertThat(reply).contains("payments need a person [claims_pay]").doesNotStartWith("Paid");
    assertThat(remote.requests).isEmpty();
    var call = RemoteA2aDenyingToolGuard.lastCall.get();
    assertThat(call).isNotNull();
    assertThat(call.toolName()).isEqualTo("claims_pay");
    assertThat(call.origin())
        .isEqualTo(
            new ToolCallGuardrail.ToolOrigin.RemoteA2a(
                "claims", "pay", "remote-a2a-guarded-test-agent"));
  }

  private String ask(String sessionId, String question) {
    return componentClient
        .forAgent()
        .inSession(sessionId)
        .method(RemoteA2aToolTestAgent::ask)
        .invoke(question);
  }

  private List<SessionMessage> storedHistory(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd())
        .messages();
  }

  /** Replies with a {@link TestModelProvider}, and records the tools offered in each request. */
  private static final class ToolRecordingModelProvider implements ModelProvider.Custom {
    private final TestModelProvider replies;
    final Queue<List<ToolSpecification>> offeredTools = new ConcurrentLinkedQueue<>();

    ToolRecordingModelProvider(TestModelProvider replies) {
      this.replies = replies;
    }

    @Override
    public String modelName() {
      return replies.modelName();
    }

    @Override
    public Object createChatModel() {
      var chatModel = (ChatModel) replies.createChatModel();
      return new ChatModel() {
        @Override
        public ChatResponse doChat(ChatRequest request) {
          offeredTools.add(request.toolSpecifications());
          return chatModel.doChat(request);
        }
      };
    }

    @Override
    public Object createStreamingChatModel() {
      return replies.createStreamingChatModel();
    }
  }

  /**
   * An A2A agent with the skills {@code check-claim} and {@code pay}. It records the params of each
   * {@code SendMessage} and answers with a message, or with an error when the message text is
   * {@code fail}.
   */
  private record FakeA2aAgent(HttpServer server, Queue<JsonNode> requests) {
    private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

    static FakeA2aAgent start() {
      try {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var agent = new FakeA2aAgent(server, new ConcurrentLinkedQueue<>());
        server.createContext("/.well-known/agent-card.json", agent::card);
        server.createContext("/a2a", agent::jsonRpc);
        server.start();
        return agent;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    String baseUrl() {
      return "http://localhost:" + server.getAddress().getPort();
    }

    String cardUrl() {
      return baseUrl() + "/.well-known/agent-card.json";
    }

    private void card(HttpExchange exchange) throws IOException {
      var card =
          new A2aAgentCard(
              "claims",
              "Answers questions about insurance claims",
              List.of(new A2aAgentCard.Interface(baseUrl() + "/a2a", "JSONRPC", null, "1.0")),
              null,
              "1.0",
              null,
              new A2aAgentCard.Capabilities(false, false, null, false),
              null,
              null,
              List.of("text/plain"),
              List.of("text/plain"),
              List.of(
                  skill("check-claim", "Checks whether a claim is valid"),
                  skill("pay", "Pays a claim")),
              null,
              null);
      respond(exchange, mapper.writeValueAsBytes(card));
    }

    private static A2aAgentSkill skill(String id, String description) {
      return new A2aAgentSkill(id, id, description, null, null, null, null, null);
    }

    private void jsonRpc(HttpExchange exchange) throws IOException {
      var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
      var params = request.get("params");
      requests.add(params);
      var text = params.path("message").path("parts").get(0).path("text").asText();
      JsonRpcResponse response;
      if (text.equals("fail")) {
        response =
            JsonRpcResponse.failure(
                request.get("id"),
                JsonRpcError.of(A2aErrorCode.INVALID_PARAMS, "no such claim", null));
      } else {
        var reply =
            new A2aMessage(
                UUID.randomUUID().toString(),
                params.path("message").path("contextId").asText(),
                null,
                A2aRole.AGENT,
                List.of(
                    A2aPart.text(text.replace("check ", "") + " is valid"),
                    A2aPart.data(java.util.Map.of("valid", true))),
                null,
                null,
                null);
        response =
            JsonRpcResponse.success(
                request.get("id"), mapper.valueToTree(A2aSendMessageResponse.of(reply)));
      }
      respond(exchange, mapper.writeValueAsBytes(response));
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) {
        out.write(body);
      }
      exchange.close();
    }
  }
}
