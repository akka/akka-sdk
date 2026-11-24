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
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.components.agent.A2aAgentIntegrationTest;
import akkajavasdk.components.agent.RecordingAllowingToolGuard;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class A2aAutonomousAgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(A2aClaimsAutonomousAgent.class, model)
        .withMockedA2aAgent(A2aAgentIntegrationTest.CLAIMS);
  }

  @AfterEach
  public void afterEach() {
    model.reset();
    A2aAgentIntegrationTest.received.clear();
    RecordingAllowingToolGuard.lastCall.set(null);
  }

  @Test
  public void shouldCallSkillOfMockedA2aAgent() {
    model
        .whenMessage(msg -> msg.contains("Look up the claim"))
        .reply(new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"claim 42\"}"));
    model
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse(completeTask("Result: " + result.content())));

    var taskId =
        componentClient
            .forAutonomousAgent(A2aClaimsAutonomousAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.STRING_TASK.instructions("Look up the claim"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.STRING_TASK);
              assertThat(snapshot.result())
                  .hasValueSatisfying(
                      value -> assertThat(value).isEqualTo("Result: Claim 42 is open"));
            });

    assertThat(A2aAgentIntegrationTest.received).containsExactly("claim 42");
    var recorded = RecordingAllowingToolGuard.lastCall.get();
    assertThat(recorded).isNotNull();
    assertThat(recorded.origin())
        .isEqualTo(new ToolCallGuardrail.ToolOrigin.RemoteA2a("claims", "lookup-claim"));
  }
}
