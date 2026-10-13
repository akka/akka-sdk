/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.ToolCallGuardrail;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akkajavasdk.components.agent.McpToolCallGuardrailTestAgent;
import akkajavasdk.components.agent.RecordingAllowingToolGuard;
import akkajavasdk.components.mcp.TestMcpEndpoint;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class AutonomousAgentGuardrailIntegrationTest extends TestKitSupport {

  private final TestModelProvider guardedModel = new TestModelProvider();
  private final TestModelProvider legacyGuardedModel = new TestModelProvider();

  private final TestModelProvider mcpGuardedModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(GuardedAutonomousAgent.class, guardedModel)
        .withModelProvider(LegacyGuardedAutonomousAgent.class, legacyGuardedModel)
        .withModelProvider(McpGuardedAutonomousAgent.class, mcpGuardedModel);
  }

  @AfterEach
  public void afterEach() {
    guardedModel.reset();
    legacyGuardedModel.reset();
    mcpGuardedModel.reset();
  }

  @Test
  public void shouldCallMcpToolWithInterceptorWhenToolCallGuardrailAllows() {
    TestMcpEndpoint.echoCalls.set(0);
    RecordingAllowingToolGuard.lastCall.set(null);
    mcpGuardedModel
        .whenMessage(msg -> msg.contains("Echo something"))
        .reply(new TestModelProvider.ToolInvocationRequest("echo", "{\"echo\":\"original\"}"));
    mcpGuardedModel
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse(completeTask("Echoed " + result.content())));

    var taskId =
        componentClient
            .forAutonomousAgent(McpGuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Echo something"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.result())
                  .hasValueSatisfying(value -> assertThat(value).contains("intercepted rewritten"));
            });

    assertThat(TestMcpEndpoint.echoCalls.get()).isEqualTo(1);

    var recorded = RecordingAllowingToolGuard.lastCall.get();
    assertThat(recorded).isNotNull();
    assertThat(recorded.toolName()).isEqualTo("echo");
    assertThat(recorded.arguments()).isEqualTo(McpGuardedAutonomousAgent.REWRITTEN_ARGUMENTS);
    assertThat(recorded.origin())
        .isEqualTo(
            new ToolCallGuardrail.ToolOrigin.RemoteMcp(McpToolCallGuardrailTestAgent.MCP_ENDPOINT));
  }

  @Test
  public void shouldFailTaskWhenModelCallGuardrailDenies() {
    guardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(GuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Say hello"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });
  }

  @Test
  public void shouldFailTaskWhenLegacyRequestGuardrailDenies() {
    legacyGuardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(LegacyGuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Tell me about the forbidden topic"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.status().name()).isEqualTo("FAILED");
            });
  }

  @Test
  public void shouldCompleteTaskWhenLegacyRequestGuardrailAllows() {
    legacyGuardedModel.fixedResponse(completeTask("done"));

    var taskId =
        componentClient
            .forAutonomousAgent(LegacyGuardedAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Tell me about the weather"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.result()).contains("done");
            });
  }
}
