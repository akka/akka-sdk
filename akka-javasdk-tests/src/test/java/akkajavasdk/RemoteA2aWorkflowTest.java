/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.actor.testkit.typed.javadsl.LoggingTestKit;
import akka.http.javadsl.model.ContentTypes;
import akka.javasdk.JsonSupport;
import akka.javasdk.a2a.A2aAgentCard;
import akka.javasdk.a2a.A2aAgentSkill;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aStreamResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.a2a.A2aTaskStatusUpdate;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.impl.a2a.A2aTaskPushNotificationConfig;
import akka.javasdk.impl.a2a.JsonRpcResponse;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.components.a2a.RemoteTaskFollowingWorkflow;
import akkajavasdk.components.agent.RemoteA2aHandOffTestAgent;
import akkajavasdk.components.agent.RemoteA2aUnreadableCardTestAgent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * A workflow sends a message to a remote A2A agent and pauses, and {@code notifyWorkflow} delivers
 * the updates of the remote task to it, by push notification or by polling. An agent's tool hands
 * off a remote task that is still working, and the outcome reaches the agent's session memory.
 */
@ExtendWith(Junit5LogCapturing.class)
public class RemoteA2aWorkflowTest extends TestKitSupport {

  private static final FakeTaskAgent remote = FakeTaskAgent.start();
  private static final String TOKEN_HEADER = "X-A2A-Notification-Token";

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(RemoteA2aHandOffTestAgent.class, model)
        .withModelProvider(RemoteA2aUnreadableCardTestAgent.class, model)
        .withAdditionalConfig(
            """
            akka.javasdk.a2a.push.public-url = "http://push.test/"
            akka.javasdk.a2a.defaults.poll-interval = 300ms
            akka.javasdk.a2a.remote-agents.push-agent.card-url = "%s"
            akka.javasdk.a2a.remote-agents.poll-agent.card-url = "%s"
            akka.javasdk.a2a.remote-agents.slow.card-url = "%s"
            akka.javasdk.a2a.remote-agents.unreachable.card-url = "http://127.0.0.1:9/.well-known/agent-card.json"
            akka.javasdk.a2a.remote-agents.unreachable.timeout = 2s
            """
                .formatted(remote.cardUrl("push"), remote.cardUrl("poll"), remote.cardUrl("poll")));
  }

  @AfterAll
  public static void stopRemote() {
    remote.server.stop(0);
  }

  @Test
  public void shouldResumeTheWorkflowOnAPushNotification() {
    var workflowId = startWorkflow("push-agent", "assess claim 1", 60_000);
    var taskId = awaitTaskId(workflowId);
    var config = awaitPushConfig(taskId);
    assertThat(config.url()).startsWith("http://push.test/a2a-push/");
    assertThat(remote.returnedImmediately).contains(taskId);

    remote.complete(taskId, "claim 1 approved");
    var status = push(config, config.token(), A2aStreamResponse.of(remote.tasks.get(taskId)));

    assertThat(status).isEqualTo(200);
    var state = awaitOutcome(workflowId);
    assertThat(state.outcome()).isEqualTo("completed");
    assertThat(state.updates())
        .containsExactly("artifact claim 1 approved", "status TASK_STATE_COMPLETED");
  }

  @Test
  public void shouldResumeTheWorkflowByPollingWhenTheRemoteAgentHasNoPush() {
    var workflowId = startWorkflow("poll-agent", "assess claim 2", 60_000);
    var taskId = awaitTaskId(workflowId);

    remote.complete(taskId, "claim 2 approved");

    var state = awaitOutcome(workflowId);
    assertThat(state.outcome()).isEqualTo("completed");
    assertThat(state.updates())
        .containsExactly("artifact claim 2 approved", "status TASK_STATE_COMPLETED");
    assertThat(remote.pushConfigs).doesNotContainKey(taskId);
  }

  @Test
  public void shouldCallThePauseTimeoutHandlerWhenTheRemoteTaskTakesTooLong() {
    var workflowId = startWorkflow("poll-agent", "assess claim 3", 1_000);
    awaitTaskId(workflowId);

    var state = awaitOutcome(workflowId);

    assertThat(state.outcome()).isEqualTo("timed out");
    assertThat(state.updates()).isEmpty();
  }

  @Test
  public void shouldRefuseAPushWithAForgedToken() {
    var workflowId = startWorkflow("push-agent", "assess claim 4", 60_000);
    var taskId = awaitTaskId(workflowId);
    var config = awaitPushConfig(taskId);
    remote.complete(taskId, "claim 4 approved");
    var body = A2aStreamResponse.of(remote.tasks.get(taskId));

    assertThat(push(config, "forged", body)).isEqualTo(401);
    assertThat(push(config, null, body)).isEqualTo(401);
    var unknown =
        new A2aTaskPushNotificationConfig(
            null, null, taskId, "http://push.test/a2a-push/" + UUID.randomUUID(), null, null);
    assertThat(push(unknown, config.token(), body)).isEqualTo(401);

    assertThat(getState(workflowId).updates()).isEmpty();
  }

  @Test
  public void shouldDeliverARepeatedPushOnce() {
    var workflowId = startWorkflow("push-agent", "assess claim 5", 60_000);
    var taskId = awaitTaskId(workflowId);
    var config = awaitPushConfig(taskId);
    var task = remote.tasks.get(taskId);
    var progress =
        new A2aTaskStatusUpdate(
            taskId,
            task.contextId(),
            new A2aTaskStatus(
                A2aTaskState.WORKING, agentMessage("Checking the policy"), Instant.now()),
            null);

    assertThat(push(config, config.token(), A2aStreamResponse.of(progress))).isEqualTo(200);
    assertThat(push(config, config.token(), A2aStreamResponse.of(progress))).isEqualTo(200);

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(getState(workflowId).updates())
                    .containsExactly("status TASK_STATE_WORKING"));
    remote.complete(taskId, "claim 5 approved");
    assertThat(push(config, config.token(), A2aStreamResponse.of(remote.tasks.get(taskId))))
        .isEqualTo(200);
    assertThat(awaitOutcome(workflowId).updates())
        .containsExactly(
            "status TASK_STATE_WORKING",
            "artifact claim 5 approved",
            "status TASK_STATE_COMPLETED");
  }

  @Test
  public void shouldWriteTheOutcomeOfAHandedOffTaskToSessionMemory() {
    model
        .whenMessage(message -> message.equals("Assess claim 6"))
        .reply(new ToolInvocationRequest("slow_assess", "{\"message\":\"assess claim 6\"}"));
    model
        .whenToolResult(result -> result.name().equals("slow_assess"))
        .thenReply(result -> new AiResponse("Tool said: " + result.content()));
    var sessionId = UUID.randomUUID().toString();

    var reply =
        componentClient
            .forAgent()
            .inSession(sessionId)
            .method(RemoteA2aHandOffTestAgent::ask)
            .invoke("Assess claim 6");

    assertThat(reply)
        .startsWith("Tool said: The remote task [")
        .contains("continues in the background");
    var taskId = remote.taskWithText("assess claim 6");
    remote.complete(taskId, "claim 6 approved");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(history(sessionId))
                    .filteredOn(SessionMessage.UserMessage.class::isInstance)
                    .map(m -> ((SessionMessage.UserMessage) m).text())
                    .anySatisfy(
                        text ->
                            assertThat(text)
                                .contains("[" + taskId + "]", "slow_assess", "claim 6 approved")));
  }

  @Test
  public void shouldDropTheToolsOfAnAgentWhoseCardCannotBeRead() {
    model.fixedResponse("Answered without the remote agent");

    var reply =
        LoggingTestKit.warn("could not be read, so its skills are not offered as tools")
            .expect(
                testKit.getActorSystem(),
                () ->
                    componentClient
                        .forAgent()
                        .inSession(UUID.randomUUID().toString())
                        .method(RemoteA2aUnreadableCardTestAgent::ask)
                        .invoke("Is claim 7 valid?"));

    assertThat(reply).isEqualTo("Answered without the remote agent");
  }

  private String startWorkflow(String agent, String text, long timeoutMillis) {
    var workflowId = UUID.randomUUID().toString();
    componentClient
        .forWorkflow(workflowId)
        .method(RemoteTaskFollowingWorkflow::start)
        .invoke(new RemoteTaskFollowingWorkflow.Start(agent, text, timeoutMillis));
    return workflowId;
  }

  private RemoteTaskFollowingWorkflow.State getState(String workflowId) {
    return componentClient
        .forWorkflow(workflowId)
        .method(RemoteTaskFollowingWorkflow::get)
        .invoke();
  }

  private String awaitTaskId(String workflowId) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> getState(workflowId).taskId() != null);
    return getState(workflowId).taskId();
  }

  private A2aTaskPushNotificationConfig awaitPushConfig(String taskId) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .until(() -> remote.pushConfigs.containsKey(taskId));
    return remote.pushConfigs.get(taskId);
  }

  private RemoteTaskFollowingWorkflow.State awaitOutcome(String workflowId) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(15, TimeUnit.SECONDS)
        .until(() -> getState(workflowId).outcome() != null);
    return getState(workflowId);
  }

  /** Sends a push notification to the URL of the configuration, as the remote agent would. */
  private int push(A2aTaskPushNotificationConfig config, String token, A2aStreamResponse body) {
    try {
      var request =
          httpClient
              .POST(URI.create(config.url()).getPath())
              .withRequestBody(
                  ContentTypes.APPLICATION_JSON,
                  JsonSupport.getObjectMapper().writeValueAsBytes(body));
      if (token != null) request = request.addHeader(TOKEN_HEADER, token);
      return request.invoke().status().intValue();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<SessionMessage> history(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd())
        .messages();
  }

  private static A2aMessage agentMessage(String text) {
    return A2aMessage.user(text).withRole(A2aRole.AGENT);
  }

  /**
   * An A2A agent with one skill, {@code assess}, served twice: under {@code /push} with a card that
   * offers push notifications, and under {@code /poll} with a card that does not. Each message
   * starts a task that stays working until the test completes it. The agent records the push
   * notification configurations and does not send push notifications itself: the test sends them.
   */
  private record FakeTaskAgent(
      HttpServer server,
      Map<String, A2aTask> tasks,
      Map<String, String> texts,
      Map<String, A2aTaskPushNotificationConfig> pushConfigs,
      Queue<String> returnedImmediately) {
    private static final ObjectMapper mapper = JsonSupport.getObjectMapper();

    static FakeTaskAgent start() {
      try {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var agent =
            new FakeTaskAgent(
                server,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                new ConcurrentLinkedQueue<>());
        for (var mode : List.of("push", "poll")) {
          server.createContext(
              "/" + mode + "/.well-known/agent-card.json", e -> agent.card(e, mode));
          server.createContext("/" + mode + "/a2a", agent::jsonRpc);
        }
        server.start();
        return agent;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    String cardUrl(String mode) {
      return "http://localhost:"
          + server.getAddress().getPort()
          + "/"
          + mode
          + "/.well-known/agent-card.json";
    }

    void complete(String taskId, String result) {
      tasks.computeIfPresent(
          taskId,
          (id, task) ->
              new A2aTask(
                  id,
                  task.contextId(),
                  new A2aTaskStatus(A2aTaskState.COMPLETED, null, Instant.now()),
                  List.of(
                      new A2aArtifact(
                          "result", null, null, List.of(A2aPart.text(result)), null, null)),
                  null,
                  null));
    }

    String taskWithText(String text) {
      return texts.entrySet().stream()
          .filter(e -> e.getValue().equals(text))
          .map(Map.Entry::getKey)
          .findFirst()
          .orElseThrow();
    }

    private void card(HttpExchange exchange, String mode) throws IOException {
      var base = "http://localhost:" + server.getAddress().getPort() + "/" + mode;
      var card =
          new A2aAgentCard(
              "assessor",
              "Assesses insurance claims",
              List.of(new A2aAgentCard.Interface(base + "/a2a", "JSONRPC", null, "1.0")),
              null,
              "1.0",
              null,
              new A2aAgentCard.Capabilities(false, mode.equals("push"), null, false),
              null,
              null,
              List.of("text/plain"),
              List.of("text/plain"),
              List.of(
                  new A2aAgentSkill(
                      "assess", "Assess", "Assesses a claim", null, null, null, null, null)),
              null,
              null);
      respond(exchange, mapper.writeValueAsBytes(card));
    }

    private void jsonRpc(HttpExchange exchange) throws IOException {
      var request = mapper.readTree(exchange.getRequestBody().readAllBytes());
      var params = request.get("params");
      JsonNode result =
          switch (request.get("method").asText()) {
            case "SendMessage" -> {
              var taskId = UUID.randomUUID().toString();
              var contextId =
                  params.path("message").path("contextId").asText(UUID.randomUUID().toString());
              texts.put(taskId, params.path("message").path("parts").get(0).path("text").asText());
              if (params.path("configuration").path("returnImmediately").asBoolean()) {
                returnedImmediately.add(taskId);
              }
              var task =
                  new A2aTask(
                      taskId,
                      contextId,
                      new A2aTaskStatus(A2aTaskState.WORKING, null, Instant.now()),
                      null,
                      null,
                      null);
              tasks.put(taskId, task);
              yield mapper.valueToTree(Map.of("task", task));
            }
            case "GetTask" -> mapper.valueToTree(tasks.get(params.get("id").asText()));
            case "CreateTaskPushNotificationConfig" -> {
              var config = mapper.treeToValue(params, A2aTaskPushNotificationConfig.class);
              var stored =
                  new A2aTaskPushNotificationConfig(
                      null, "config-1", config.taskId(), config.url(), config.token(), null);
              pushConfigs.put(config.taskId(), stored);
              yield mapper.valueToTree(stored);
            }
            default -> mapper.createObjectNode();
          };
      respond(
          exchange, mapper.writeValueAsBytes(JsonRpcResponse.success(request.get("id"), result)));
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
