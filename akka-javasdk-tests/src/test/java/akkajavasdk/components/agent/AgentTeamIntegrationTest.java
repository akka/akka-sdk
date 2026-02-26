/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.AgentTeam;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// @ExtendWith(Junit5LogCapturing.class)
public class AgentTeamIntegrationTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();
  private final TestModelProvider workerModelProvider = new TestModelProvider();
  private final TestModelProvider childOrchestratorModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(SimpleOrchestratorAgent.class, testModelProvider)
        .withModelProvider(SimpleWorkerAgent.class, workerModelProvider)
        .withModelProvider(ChildOrchestratorAgent.class, childOrchestratorModelProvider);
  }

  @AfterEach
  public void afterEach() {
    testModelProvider.reset();
    workerModelProvider.reset();
    childOrchestratorModelProvider.reset();
  }

  private String newSessionId() {
    return UUID.randomUUID().toString();
  }

  @Test
  public void shouldCompleteImmediatelyWhenModelRespondsWithResult() {
    // given
    testModelProvider.fixedResponse("done");
    var sessionId = newSessionId();

    // when
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SimpleOrchestratorAgent::run)
        .invoke("task");

    // then
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forAgent()
                      .inSession(sessionId)
                      .method(SimpleOrchestratorAgent::getResult)
                      .invoke();
              assertThat(result).isInstanceOf(AgentTeam.Result.Completed.class);
              var completed = (AgentTeam.Result.Completed<?>) result;
              assertThat(completed.result()).isEqualTo("done");
            });
  }

  @Test
  public void shouldCallToolAndThenCompleteWithResult() {
    // given - model first requests a tool call, then completes with the result
    testModelProvider
        .whenMessage(msg -> true)
        .reply(new ToolInvocationRequest("DateTools_getDate", ""));

    testModelProvider
        .whenToolResult(result -> result.content().equals("2025-01-15"))
        .reply("today is 2025-01-15");

    var sessionId = newSessionId();

    // when
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SimpleOrchestratorAgent::run)
        .invoke("What is today's date?");

    // then
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forAgent()
                      .inSession(sessionId)
                      .method(SimpleOrchestratorAgent::getResult)
                      .invoke();
              assertThat(result).isInstanceOf(AgentTeam.Result.Completed.class);
              var completed = (AgentTeam.Result.Completed<?>) result;
              assertThat(completed.result()).isEqualTo("today is 2025-01-15");
            });
  }

  @Test
  public void shouldDelegateToAnotherAgentAndThenCompleteWithResult() {
    // given - orchestrator delegates to simple-worker, then completes with aggregated result
    testModelProvider
        .whenMessage(msg -> true)
        .reply(new ToolInvocationRequest("delegate_to_simple_worker", "{\"task\": \"help me\"}"));

    testModelProvider
        .whenToolResult(result -> result.name().equals("delegate_to_simple_worker"))
        .reply("all done");

    workerModelProvider.fixedResponse("worker done");

    var sessionId = newSessionId();

    // when
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SimpleOrchestratorAgent::run)
        .invoke("do some work");

    // then
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forAgent()
                      .inSession(sessionId)
                      .method(SimpleOrchestratorAgent::getResult)
                      .invoke();
              assertThat(result).isInstanceOf(AgentTeam.Result.Completed.class);
              var completed = (AgentTeam.Result.Completed<?>) result;
              assertThat(completed.result()).isEqualTo("all done");
            });
  }

  @Test
  public void shouldDelegateToAnotherAgentTeamAndCompleteWhenChildReturnsResult() {
    // given - orchestrator delegates to child-orchestrator, child completes, parent then completes
    testModelProvider
        .whenMessage(msg -> true)
        .reply(
            new ToolInvocationRequest("delegate_to_child_orchestrator", "{\"task\": \"subtask\"}"));

    testModelProvider
        .whenToolResult(result -> result.name().equals("delegate_to_child_orchestrator"))
        .reply("parent done");

    childOrchestratorModelProvider.fixedResponse("child done");

    var sessionId = newSessionId();

    // when
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SimpleOrchestratorAgent::run)
        .invoke("do some work");

    // then
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forAgent()
                      .inSession(sessionId)
                      .method(SimpleOrchestratorAgent::getResult)
                      .invoke();
              assertThat(result).isInstanceOf(AgentTeam.Result.Completed.class);
              var completed = (AgentTeam.Result.Completed<?>) result;
              assertThat(completed.result()).isEqualTo("parent done");
            });
  }

  @Test
  public void shouldFailWhenModelCallsTheFailTool() {
    // given - model calls the built-in fail tool with a reason
    testModelProvider
        .whenMessage(msg -> true)
        .reply(new ToolInvocationRequest("fail", "{\"reason\": \"cannot complete the task\"}"));

    var sessionId = newSessionId();

    // when
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SimpleOrchestratorAgent::run)
        .invoke("do something impossible");

    // then
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var result =
                  componentClient
                      .forAgent()
                      .inSession(sessionId)
                      .method(SimpleOrchestratorAgent::getResult)
                      .invoke();
              assertThat(result).isInstanceOf(AgentTeam.Result.Failed.class);
              var failed = (AgentTeam.Result.Failed<?>) result;
              assertThat(failed.reason()).isEqualTo("cannot complete the task");
            });
  }
}
