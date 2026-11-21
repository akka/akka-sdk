/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aAgentSkill;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.agent.ToolCallGuardrail;
import akka.javasdk.impl.a2a.A2aTracing;
import akka.javasdk.impl.a2a.JsonRpcResponse;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.components.a2a.A2aEchoAgent;
import akkajavasdk.components.a2a.A2aGovernanceGuard;
import akkajavasdk.components.a2a.RemoteA2aGovernedWorkflow;
import akkajavasdk.components.agent.RemoteA2aGovernedTestAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * The same guardrails and sanitizers govern a remote A2A agent called as an agent's tool and called
 * from a workflow step, and both directions of A2A are traced.
 */
@ExtendWith(Junit5LogCapturing.class)
public class RemoteA2aGovernanceTest extends TestKitSupport {

  private static final FakeAgents remote = FakeAgents.start();
  private static final String WORKFLOW = "remote-a2a-governed-workflow";
  private static final String AGENT = "remote-a2a-governed-test-agent";

  private final TestModelProvider model = new TestModelProvider();
  private final TestModelProvider echoModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    echoModel.fixedResponse(input -> new AiResponse("echo: " + input.content()));
    return TestKit.Settings.DEFAULT
        .withModelProvider(RemoteA2aGovernedTestAgent.class, model)
        .withModelProvider(A2aEchoAgent.class, echoModel)
        .withAdditionalConfig(
            """
            akka.javasdk.a2a.remote-agents.claims.card-url = "%s"
            akka.javasdk.a2a.remote-agents.open.card-url = "%s"
            akka.javasdk.agent.guardrails {
              "a2a governance guard" {
                class = "akkajavasdk.components.a2a.A2aGovernanceGuard"
                agents = ["%s", "%s"]
                category = TOOL_POLICY
                tool-origins = [remote-a2a]
              }
              "deny claims by name" {
                class = "akkajavasdk.components.a2a.A2aScopedDenyGuard"
                agents = ["%s"]
                category = TOOL_POLICY
                remote-agents = ["claims"]
              }
              "deny archive by card url" {
                class = "akkajavasdk.components.a2a.A2aScopedDenyGuard"
                agents = ["%s"]
                category = TOOL_POLICY
                tool-origins = [remote-a2a]
                remote-agents = ["%s"]
              }
            }
            akka.javasdk.sanitization.sanitizers.claim-ids {
              pattern = "CLAIM-[0-9]+"
              apply-at = ["tool-result"]
              agents = ["%s", "%s"]
            }
            """
                .formatted(
                    remote.cardUrl("claims"),
                    remote.cardUrl("open"),
                    AGENT,
                    WORKFLOW,
                    WORKFLOW,
                    WORKFLOW,
                    remote.cardUrl("archive"),
                    AGENT,
                    WORKFLOW));
  }

  @AfterAll
  public static void stopRemote() {
    remote.server.stop(0);
  }

  @Test
  public void shouldBlockAToolCallAndAWorkflowStepWithTheSameGuardrail() {
    model
        .whenMessage(message -> message.equals("Pay claim 1"))
        .reply(new ToolInvocationRequest("claims_pay", "{\"message\":\"pay claim 1\"}"));
    model
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse("Paid " + result.content()));
    var sessionId = UUID.randomUUID().toString();

    var reply =
        componentClient
            .forAgent()
            .inSession(sessionId)
            .method(RemoteA2aGovernedTestAgent::ask)
            .invoke("Pay claim 1");

    assertThat(reply).contains("payments need a person").doesNotStartWith("Paid");
    // The agent loop runs the guardrail once for the tool call
    assertThat(A2aGovernanceGuard.calls)
        .filteredOn(call -> call.sessionId().equals(sessionId))
        .singleElement()
        .satisfies(call -> assertThat(call.agentId()).isEqualTo(AGENT));

    var workflowId = run(new RemoteA2aGovernedWorkflow.Call("claims", null, "pay", "pay claim 2"));

    assertThat(outcome(workflowId))
        .isEqualTo(
            "denied: Guardrail [a2a governance guard] denied the call: payments need a person");
    assertThat(remote.texts).doesNotContain("pay claim 1", "pay claim 2");
    assertThat(A2aGovernanceGuard.calls)
        .filteredOn(call -> call.sessionId().equals(workflowId))
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.agentId()).isEqualTo(WORKFLOW);
              assertThat(call.toolName()).isEqualTo("claims_pay");
              assertThat(call.origin())
                  .isEqualTo(
                      new ToolCallGuardrail.ToolOrigin.RemoteA2a(
                          "claims", remote.cardUrl("claims"), "pay"));
              assertThat(call.arguments()).contains("pay claim 2");
            });
  }

  @Test
  public void shouldMatchRemoteAgentsByNameAndByCardUrl() {
    var byName = run(new RemoteA2aGovernedWorkflow.Call("claims", null, "check-claim", "check 3"));
    var byUrl =
        run(
            new RemoteA2aGovernedWorkflow.Call(
                null, remote.cardUrl("archive"), "check-claim", "check 4"));
    var other = run(new RemoteA2aGovernedWorkflow.Call("open", null, "check-claim", "check 5"));

    assertThat(outcome(byName)).startsWith("denied: Guardrail [deny claims by name]");
    assertThat(outcome(byUrl)).startsWith("denied: Guardrail [deny archive by card url]");
    assertThat(outcome(other)).startsWith("reply: ");
  }

  @Test
  public void shouldMaskTheReplyOfARemoteAgentOnTheWorkflowPath() {
    var workflowId =
        run(new RemoteA2aGovernedWorkflow.Call("open", null, "check-claim", "check 6"));

    assertThat(outcome(workflowId))
        .isEqualTo("reply: claim ********** is valid\n{\"ref\":\"**********\"}")
        .doesNotContain("CLAIM-1234");
  }

  @Test
  public void shouldMaskTheReplyOfARemoteAgentOnTheToolPath() {
    model
        .whenMessage(message -> message.equals("Check claim 7"))
        .reply(new ToolInvocationRequest("claims_check-claim", "{\"message\":\"check 7\"}"));
    model
        .whenToolResult(result -> result.name().equals("claims_check-claim"))
        .thenReply(result -> new AiResponse("Seen: " + result.content()));

    var reply =
        componentClient
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(RemoteA2aGovernedTestAgent::ask)
            .invoke("Check claim 7");

    assertThat(reply).startsWith("Seen: ").contains("**********").doesNotContain("CLAIM-1234");
  }

  @Test
  public void shouldTraceACallToARemoteAgent() {
    var workflowId =
        run(new RemoteA2aGovernedWorkflow.Call("open", null, "check-claim", "check 8"));
    outcome(workflowId);

    var span =
        awaitSpan(
            s ->
                s.getName().equals("A2A SendMessage")
                    && workflowId.equals(s.getAttributes().get(A2aTracing.CONTEXT_ID)));

    assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
    var attributes = span.getAttributes();
    assertThat(attributes.get(A2aTracing.METHOD)).isEqualTo("SendMessage");
    assertThat(attributes.get(A2aTracing.REMOTE_AGENT_NAME)).isEqualTo("open");
    assertThat(attributes.get(A2aTracing.REMOTE_CARD_URL)).isEqualTo(remote.cardUrl("open"));
    assertThat(attributes.get(A2aTracing.SKILL_ID)).isEqualTo("check-claim");
    assertThat(attributes.get(A2aTracing.TASK_ID)).isNotBlank();
    assertThat(attributes.get(A2aTracing.TASK_STATE)).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(attributes.get(A2aTracing.CALLER_COMPONENT_ID)).isEqualTo(WORKFLOW);
    assertThat(span.getEndEpochNanos()).isGreaterThan(span.getStartEpochNanos());
  }

  @Test
  public void shouldTraceACallToTheA2aEndpoint() throws Exception {
    var contextId = UUID.randomUUID().toString();
    var message =
        Map.of(
            "messageId",
            UUID.randomUUID().toString(),
            "role",
            "ROLE_USER",
            "contextId",
            contextId,
            "parts",
            List.of(Map.of("text", "hello")),
            "metadata",
            Map.of("skillId", "echo"));
    var request =
        Map.of(
            "jsonrpc",
            "2.0",
            "id",
            1,
            "method",
            "SendMessage",
            "params",
            Map.of("message", message));

    var response =
        httpClient
            .POST("/a2a")
            .addHeader("Authorization", token("alice"))
            .withRequestBody(
                ContentTypes.APPLICATION_JSON, FakeAgents.mapper.writeValueAsBytes(request))
            .invoke();
    assertThat(response.body().utf8String()).contains("TASK_STATE_COMPLETED");

    var span =
        awaitSpan(
            s ->
                s.getName().equals("A2A SendMessage")
                    && contextId.equals(s.getAttributes().get(A2aTracing.CONTEXT_ID))
                    && s.getKind() != SpanKind.CLIENT);
    var attributes = span.getAttributes();
    assertThat(attributes.get(A2aTracing.METHOD)).isEqualTo("SendMessage");
    assertThat(attributes.get(A2aTracing.SKILL_ID)).isEqualTo("echo");
    assertThat(attributes.get(A2aTracing.TASK_ID)).isNotBlank();
    assertThat(attributes.get(A2aTracing.CALLER_SUBJECT)).isEqualTo("alice");
    assertThat(attributes.get(A2aTracing.TASK_STATE)).isEqualTo("TASK_STATE_COMPLETED");
  }

  private SpanData awaitSpan(java.util.function.Predicate<SpanData> matches) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                testKit.getInMemorySpanExporter().getFinishedSpanItems().stream()
                    .anyMatch(matches));
    return testKit.getInMemorySpanExporter().getFinishedSpanItems().stream()
        .filter(matches)
        .findFirst()
        .orElseThrow();
  }

  private String run(RemoteA2aGovernedWorkflow.Call call) {
    var workflowId = UUID.randomUUID().toString();
    componentClient.forWorkflow(workflowId).method(RemoteA2aGovernedWorkflow::start).invoke(call);
    return workflowId;
  }

  private String outcome(String workflowId) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                componentClient
                        .forWorkflow(workflowId)
                        .method(RemoteA2aGovernedWorkflow::get)
                        .invoke()
                        .outcome()
                    != null);
    return componentClient
        .forWorkflow(workflowId)
        .method(RemoteA2aGovernedWorkflow::get)
        .invoke()
        .outcome();
  }

  // A token without signature, which the runtime accepts in tests
  private static String token(String subject) throws Exception {
    var header = Base64.getEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
    var claims =
        Base64.getEncoder()
            .encodeToString(
                FakeAgents.mapper.writeValueAsBytes(
                    Map.of("iss", "a2a-test-issuer", "sub", subject)));
    return "Bearer " + header + "." + claims;
  }

  /**
   * Three A2A agents, {@code claims}, {@code archive} and {@code open}, with the skills {@code
   * check-claim} and {@code pay}. {@code check-claim} answers with a completed task whose artifact
   * names a claim id, and {@code pay} with a message. The agents record the text of each message.
   */
  private record FakeAgents(HttpServer server, Queue<String> texts) {
    static final ObjectMapper mapper = JsonSupport.getObjectMapper();

    static FakeAgents start() {
      try {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var agents = new FakeAgents(server, new ConcurrentLinkedQueue<>());
        for (var name : List.of("claims", "archive", "open")) {
          server.createContext(
              "/" + name + "/.well-known/agent-card.json", e -> agents.card(e, name));
          server.createContext("/" + name + "/a2a", agents::jsonRpc);
        }
        server.start();
        return agents;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    String baseUrl(String name) {
      return "http://localhost:" + server.getAddress().getPort() + "/" + name;
    }

    String cardUrl(String name) {
      return baseUrl(name) + "/.well-known/agent-card.json";
    }

    private void card(HttpExchange exchange, String name) throws IOException {
      var card =
          new A2aAgentCard(
              name + "-agent",
              "Answers questions about insurance claims",
              List.of(new A2aAgentCard.Interface(baseUrl(name) + "/a2a", "JSONRPC", null, "1.0")),
              null,
              "1.0",
              null,
              new A2aAgentCard.Capabilities(false, false, null, false),
              null,
              null,
              List.of("text/plain"),
              List.of("text/plain"),
              List.of(skill("check-claim"), skill("pay")),
              null,
              null);
      respond(exchange, mapper.writeValueAsBytes(card));
    }

    private static A2aAgentSkill skill(String id) {
      return new A2aAgentSkill(id, id, id, null, null, null, null, null);
    }

    private void jsonRpc(HttpExchange exchange) throws IOException {
      var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
      JsonNode params = request.get("params");
      var text = params.path("message").path("parts").get(0).path("text").asText();
      texts.add(text);
      var contextId = params.path("message").path("contextId").asText();
      var skill = params.path("metadata").path("skillId").asText();
      A2aSendMessageResponse reply;
      if (skill.equals("pay")) {
        reply =
            A2aSendMessageResponse.of(
                new A2aMessage(
                    UUID.randomUUID().toString(),
                    contextId,
                    null,
                    A2aRole.AGENT,
                    List.of(A2aPart.text("paid")),
                    null,
                    null,
                    null));
      } else {
        reply =
            A2aSendMessageResponse.of(
                new A2aTask(
                    UUID.randomUUID().toString(),
                    contextId,
                    new A2aTaskStatus(A2aTaskState.COMPLETED, null, Instant.now()),
                    List.of(
                        new A2aArtifact(
                            "result",
                            null,
                            null,
                            List.of(
                                A2aPart.text("claim CLAIM-1234 is valid"),
                                A2aPart.data(Map.of("ref", "CLAIM-1234"))),
                            null,
                            null)),
                    null,
                    null));
      }
      respond(
          exchange,
          mapper.writeValueAsBytes(
              JsonRpcResponse.success(request.get("id"), mapper.valueToTree(reply))));
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
