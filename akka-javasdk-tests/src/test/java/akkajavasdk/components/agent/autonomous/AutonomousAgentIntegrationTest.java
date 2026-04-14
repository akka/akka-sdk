/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.handoffTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.components.agent.SomeAgent;
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

    var taskId =
        componentClient
            .forAutonomousAgent(CoordinatorAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESEARCH);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().title()).isEqualTo("Quantum Computing Summary");
              assertThat(snapshot.result().summary()).contains("parallel computation");
            });
  }

  @Test
  public void shouldHandoffToSpecialist() {
    triageModel.fixedResponse(
        handoffTo(SpecialistTestAgent.class, "Customer has a billing dispute."));

    specialistModel.fixedResponse(
        completeTask(new TestTasks.SupportResolution("billing", "Refund issued.", true)));

    var taskId =
        componentClient
            .forAutonomousAgent(TriageTestAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESOLVE.instructions("I was charged twice."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.RESOLVE);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().category()).isEqualTo("billing");
              assertThat(snapshot.result().resolved()).isTrue();
            });
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
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().value()).isEqualTo("Claim verified.");
              assertThat(snapshot.result().score()).isEqualTo(90);
            });
  }
}
