/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.handoffTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.AgentRegistry;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.components.agent.SomeAgent;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Smoke tests exercising cross-capability autonomous agent flows: delegation, handoff,
 * request-based delegation, and end-to-end lifecycle notifications.
 */
public class AutonomousAgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider workerModel = new TestModelProvider();
  private final TestModelProvider triageModel = new TestModelProvider();
  private final TestModelProvider specialistModel = new TestModelProvider();
  private final TestModelProvider requestDelegatingModel = new TestModelProvider();
  private final TestModelProvider someAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel)
        .withModelProvider(TriageTestAgent.class, triageModel)
        .withModelProvider(SpecialistTestAgent.class, specialistModel)
        .withModelProvider(RequestDelegatingAgent.class, requestDelegatingModel)
        .withModelProvider(SomeAgent.class, someAgentModel);
  }

  @AfterEach
  public void afterEach() {
    coordinatorModel.reset();
    workerModel.reset();
    triageModel.reset();
    specialistModel.reset();
    requestDelegatingModel.reset();
    someAgentModel.reset();
  }

  @Test
  public void shouldDelegateToWorkerAndSynthesizeResult() {
    coordinatorModel
        .whenMessage(msg -> msg.contains("quantum"))
        .reply(
            delegateTo(
                TestTasks.FINDINGS, WorkerAgent.class, "Research quantum computing fundamentals"));

    workerModel.fixedResponse(
        completeTask(
            new TestTasks.FindingsResult(
                "Quantum Computing", "Qubits enable parallel computation.")));

    coordinatorModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(
            completeTask(
                new TestTasks.ResearchResult(
                    "Quantum Computing Summary", "Qubits enable parallel computation.")));

    var coordinatorId = UUID.randomUUID().toString();
    var coordinatorClient =
        componentClient.forAutonomousAgent(CoordinatorAgent.class, coordinatorId);

    var coordinatorNotifications = new ArrayList<Notification>();
    coordinatorClient
        .notificationStream()
        .runForeach(coordinatorNotifications::add, testKit.getMaterializer());

    var taskId =
        coordinatorClient.runSingleTask(
            TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESEARCH);
              var result = snapshot.result().orElseThrow();
              assertThat(result.title()).isEqualTo("Quantum Computing Summary");
              assertThat(result.summary()).contains("parallel computation");
            });

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var delegationStarted =
                  coordinatorNotifications.stream()
                      .filter(n -> n instanceof Notification.DelegationStarted)
                      .map(n -> (Notification.DelegationStarted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(delegationStarted.delegationCount()).isEqualTo(1);
              assertThat(delegationStarted.workerComponentIds()).containsExactly("worker-agent");
              assertThat(delegationStarted.subtaskIds()).hasSize(1);

              var delegationResolved =
                  coordinatorNotifications.stream()
                      .filter(n -> n instanceof Notification.DelegationResolved)
                      .map(n -> (Notification.DelegationResolved) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(delegationResolved.succeeded()).isEqualTo(1);
              assertThat(delegationResolved.failed()).isZero();
              assertThat(delegationResolved.succeededSubtaskIds())
                  .containsExactlyElementsOf(delegationStarted.subtaskIds());
            });
  }

  @Test
  public void shouldHandoffToSpecialist() {
    triageModel.fixedResponse(
        handoffTo(SpecialistTestAgent.class, "Customer has a billing dispute."));

    specialistModel.fixedResponse(
        completeTask(new TestTasks.SupportResolution("billing", "Refund issued.", true)));

    var triageId = UUID.randomUUID().toString();
    var triageClient = componentClient.forAutonomousAgent(TriageTestAgent.class, triageId);

    var triageNotifications = new ArrayList<Notification>();
    triageClient
        .notificationStream()
        .runForeach(triageNotifications::add, testKit.getMaterializer());

    var taskId = triageClient.runSingleTask(TestTasks.RESOLVE.instructions("I was charged twice."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESOLVE);
              var result = snapshot.result().orElseThrow();
              assertThat(result.category()).isEqualTo("billing");
              assertThat(result.resolved()).isTrue();
            });

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var handoffStarted =
                  triageNotifications.stream()
                      .filter(n -> n instanceof Notification.HandoffStarted)
                      .map(n -> (Notification.HandoffStarted) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(handoffStarted.taskId()).isEqualTo(taskId);
              assertThat(handoffStarted.targetComponentId()).isEqualTo("specialist-agent");
              assertThat(handoffStarted.targetInstanceId()).isNotBlank();
            });
  }

  @Test
  public void shouldIncludeAutonomousAgentsInRegistry() {
    assertThat(
            testKit.getAgentRegistry().allAgents().stream()
                .map(AgentRegistry.AgentInfo::id)
                .toList())
        .contains(
            "coordinator-agent",
            "worker-agent",
            "triage-test-agent",
            "specialist-agent",
            "request-delegating-agent");

    assertThat(
            testKit.getAgentRegistry().agentsWithRole("billing-specialist").stream()
                .map(AgentRegistry.AgentInfo::id)
                .toList())
        .isEqualTo(List.of("specialist-agent"));

    var specialistInfo = testKit.getAgentRegistry().agentInfo("specialist-agent");
    assertThat(specialistInfo.id()).isEqualTo("specialist-agent");
    assertThat(specialistInfo.name()).isEqualTo("Specialist");
    assertThat(specialistInfo.description()).isEqualTo("Resolves billing disputes.");
    assertThat(specialistInfo.role()).isEqualTo("billing-specialist");

    // CoordinatorAgent doesn't define name, description, or role — defaults apply
    var coordinatorInfo = testKit.getAgentRegistry().agentInfo("coordinator-agent");
    assertThat(coordinatorInfo.id()).isEqualTo("coordinator-agent");
    assertThat(coordinatorInfo.name()).isEqualTo("coordinator-agent");
    assertThat(coordinatorInfo.description()).isEqualTo("");
    assertThat(coordinatorInfo.role()).isEqualTo("");
  }

  @Test
  public void shouldDelegateToRequestBasedAgent() {
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("verify"))
        .reply(delegateTo(SomeAgent.class, "{\"claim\":\"The sky is blue.\"}"));

    someAgentModel.fixedResponse("{\"response\":\"Verified: the sky is indeed blue.\"}");

    requestDelegatingModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(completeTask(new TestTasks.TestResult("Claim verified.", 90)));

    var taskId =
        componentClient
            .forAutonomousAgent(RequestDelegatingAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.TEST_TASK.instructions("Please verify: the sky is blue."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.TEST_TASK);
              var result = snapshot.result().orElseThrow();
              assertThat(result.value()).isEqualTo("Claim verified.");
              assertThat(result.score()).isEqualTo(90);
            });
  }
}
