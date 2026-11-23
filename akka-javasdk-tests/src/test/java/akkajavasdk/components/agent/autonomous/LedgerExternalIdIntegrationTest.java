/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.ledger.ActivityRecord;
import akka.javasdk.ledger.FlowRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akkajavasdk.components.agent.SomeAgent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** Reads the records of the calls that carry an external id from the ledger. */
public class LedgerExternalIdIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider workerModel = new TestModelProvider();
  private final TestModelProvider someAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel)
        .withModelProvider(SomeAgent.class, someAgentModel);
  }

  @Test
  public void readsTheInteractionOfARequestBasedAgentCallWithAnExternalId() {
    someAgentModel.fixedResponse("hi");
    var externalId = "call-" + UUID.randomUUID();

    var withExternalId =
        componentClient
            .withExternalId(externalId)
            .forAgent()
            .inSession(UUID.randomUUID().toString())
            .method(SomeAgent::mapLlmResponse)
            .withDetailedReply()
            .invoke("hello");
    // a call without the external id
    componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(SomeAgent::mapLlmResponse)
        .invoke("hello");

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(interactions(getLedgerClient().getExternalIdRecords(externalId)))
                    .extracting(InteractionRecord::interactionId)
                    .containsExactly(withExternalId.interactionId().orElseThrow()));
  }

  @Test
  public void readsTheRecordsOfADelegatingAutonomousAgentStartedWithAnExternalId() {
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

    var externalId = "call-" + UUID.randomUUID();
    var taskId =
        componentClient
            .withExternalId(externalId)
            .forAutonomousAgent(CoordinatorAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.RESEARCH).result())
                    .isPresent());

    // the ledger records the activity after the task completes
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var records = getLedgerClient().getExternalIdRecords(externalId);

              // the worker runs in the flow of the coordinator, so its records carry the id too
              assertThat(interactions(records))
                  .extracting(InteractionRecord::agentComponentId)
                  .contains("coordinator-agent", "worker-agent");

              assertThat(records)
                  .filteredOn(record -> record instanceof ActivityRecord)
                  .flatExtracting(record -> ((ActivityRecord) record).events())
                  .anySatisfy(
                      event ->
                          assertThat(event)
                              .isInstanceOfSatisfying(
                                  ActivityRecord.Event.TaskCompleted.class,
                                  completed -> assertThat(completed.taskId()).isEqualTo(taskId)));
            });
  }

  private static List<InteractionRecord> interactions(List<FlowRecord> records) {
    return records.stream()
        .filter(record -> record instanceof InteractionRecord)
        .map(record -> (InteractionRecord) record)
        .toList();
  }
}
