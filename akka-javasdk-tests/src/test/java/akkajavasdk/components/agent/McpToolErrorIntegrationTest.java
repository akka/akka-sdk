/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akkajavasdk.components.agent.RequestRecordingModelProvider.messagesOf;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akka.stream.javadsl.Sink;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.agent.RecordingMcpToolInterceptor.SeenResponse;
import akkajavasdk.components.agent.autonomous.McpToolErrorAutonomousTestAgent;
import akkajavasdk.components.agent.autonomous.TestTasks;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Covers the error flag of a failed MCP tool call, from the MCP server through a tool interceptor
 * and session memory, and back to the model in a later request.
 */
@ExtendWith(Junit5LogCapturing.class)
public class McpToolErrorIntegrationTest extends TestKitSupport {

  private final TestModelProvider requestModel = new TestModelProvider();
  private final RequestRecordingModelProvider requestModelRequests =
      new RequestRecordingModelProvider(requestModel);
  private final TestModelProvider streamingModel = new TestModelProvider();
  private final RequestRecordingModelProvider streamingModelRequests =
      new RequestRecordingModelProvider(streamingModel);
  private final TestModelProvider autonomousModel = new TestModelProvider();
  private final RequestRecordingModelProvider autonomousModelRequests =
      new RequestRecordingModelProvider(autonomousModel);

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(McpToolErrorTestAgent.class, requestModelRequests)
        .withModelProvider(McpToolErrorStreamingTestAgent.class, streamingModelRequests)
        .withModelProvider(McpToolErrorAutonomousTestAgent.class, autonomousModelRequests);
  }

  @AfterEach
  public void afterEach() {
    requestModel.reset();
    requestModelRequests.reset();
    streamingModel.reset();
    streamingModelRequests.reset();
    autonomousModel.reset();
    autonomousModelRequests.reset();
    RecordingMcpToolInterceptor.reset();
  }

  /** The model calls the failing tool, then answers, and answers a follow-up question. */
  private static void callFailingToolThenAnswer(TestModelProvider model) {
    model
        .whenMessage(message -> message.contains("use the tool"))
        .reply(new ToolInvocationRequest("fail", "{\"reason\":\"broken\"}"));
    model
        .whenToolResult(result -> result.name().equals("fail"))
        .thenReply(result -> new AiResponse("the tool failed"));
    model.whenMessage(message -> message.contains("anything else")).reply("no");
  }

  @Test
  public void shouldKeepTheErrorFlagOfAnMcpToolResultForARequestBasedAgent() {
    callFailingToolThenAnswer(requestModel);
    var sessionId = UUID.randomUUID().toString();

    askRequestAgent(sessionId, "use the tool");

    assertErrorResultSent(requestModelRequests.lastRequest());
    assertThat(RecordingMcpToolInterceptor.seen("mcp-tool-error-test-agent"))
        .contains(new SeenResponse("mcp-tool-error-test-agent", "fail", true));
    assertErrorResultStored(sessionId);

    askRequestAgent(sessionId, "anything else?");

    assertErrorResultSent(requestModelRequests.lastRequest());
  }

  @Test
  public void shouldKeepTheErrorFlagOfAnMcpToolResultForAStreamingAgent() throws Exception {
    callFailingToolThenAnswer(streamingModel);
    var sessionId = UUID.randomUUID().toString();

    askStreamingAgent(sessionId, "use the tool");

    assertErrorResultSent(streamingModelRequests.lastRequest());
    assertThat(RecordingMcpToolInterceptor.seen("mcp-tool-error-streaming-test-agent"))
        .contains(new SeenResponse("mcp-tool-error-streaming-test-agent", "fail", true));
    assertErrorResultStored(sessionId);

    askStreamingAgent(sessionId, "anything else?");

    assertErrorResultSent(streamingModelRequests.lastRequest());
  }

  @Test
  public void shouldKeepTheErrorFlagOfAnMcpToolResultForAnAutonomousAgent() {
    autonomousModel
        .whenMessage(message -> message.contains("use the tool"))
        .reply(new ToolInvocationRequest("fail", "{\"reason\":\"broken\"}"));
    autonomousModel
        .whenToolResult(result -> result.name().equals("fail"))
        .thenReply(result -> new AiResponse(completeTask("the tool failed")));
    // the runtime asks the model to continue when the agent picks up the next task
    autonomousModel
        .whenMessage(
            message -> message.contains("second task") || message.contains("Continue working"))
        .reply(new AiResponse(completeTask("nothing to do")));
    var agentId = UUID.randomUUID().toString();

    runAutonomousTask(agentId, "use the tool");

    assertErrorResultSent(autonomousModelRequests.lastRequest());
    assertThat(RecordingMcpToolInterceptor.seen("mcp-tool-error-autonomous-agent"))
        .contains(new SeenResponse("mcp-tool-error-autonomous-agent", "fail", true));
    assertErrorResultStored(agentId);

    runAutonomousTask(agentId, "second task");

    // the runtime reads the earlier task from session memory for the next task
    assertErrorResultSent(autonomousModelRequests.lastRequest());
  }

  private void askRequestAgent(String sessionId, String question) {
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(McpToolErrorTestAgent::query)
        .invoke(question);
  }

  private void askStreamingAgent(String sessionId, String question) throws Exception {
    componentClient
        .forAgent()
        .inSession(sessionId)
        .tokenStream(McpToolErrorStreamingTestAgent::ask)
        .source(question)
        .runWith(Sink.seq(), testKit.getMaterializer())
        .toCompletableFuture()
        .get(10, TimeUnit.SECONDS);
  }

  private void runAutonomousTask(String agentId, String instructions) {
    var taskId =
        componentClient
            .forTask(UUID.randomUUID().toString())
            .create(TestTasks.STRING_TASK.instructions(instructions));
    componentClient
        .forAutonomousAgent(McpToolErrorAutonomousTestAgent.class, agentId)
        .assignTasks(taskId);

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.STRING_TASK).result())
                    .isPresent());
  }

  private static void assertErrorResultSent(List<ChatMessage> request) {
    assertThat(messagesOf(request, ToolExecutionResultMessage.class))
        .filteredOn(result -> result.toolName().equals("fail"))
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.toolName()).isEqualTo("fail");
              assertThat(result.text()).startsWith(RecordingMcpToolInterceptor.PREFIX);
              assertThat(result.isError()).isTrue();
            });
  }

  private void assertErrorResultStored(String sessionId) {
    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(storedHistory(sessionId))
                    .filteredOn(ToolCallResponse.class::isInstance)
                    .map(ToolCallResponse.class::cast)
                    .filteredOn(response -> response.name().equals("fail"))
                    .singleElement()
                    .satisfies(
                        response -> {
                          assertThat(response.name()).isEqualTo("fail");
                          assertThat(response.text())
                              .startsWith(RecordingMcpToolInterceptor.PREFIX);
                          assertThat(response.isError()).isTrue();
                        }));
  }

  private List<SessionMessage> storedHistory(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd())
        .messages();
  }
}
