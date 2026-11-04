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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/** Reads the records of the flow of a delegating autonomous agent from the ledger. */
public class LedgerFlowIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider workerModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel);
  }

  @Test
  public void readsTheFlowOfADelegatingAutonomousAgent() {
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
    var taskId =
        componentClient
            .forAutonomousAgent(CoordinatorAgent.class, coordinatorId)
            .runSingleTask(TestTasks.RESEARCH.instructions("Research quantum computing"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.RESEARCH).result())
                    .isPresent());

    // the session of an autonomous agent interaction is the id of the agent instance
    var flowId =
        Awaitility.await()
            .atMost(10, TimeUnit.SECONDS)
            .until(
                () ->
                    getLedgerClient().getInteractions("coordinator-agent", coordinatorId).stream()
                        .flatMap(interaction -> interaction.flowId().stream())
                        .findFirst(),
                Optional::isPresent)
            .orElseThrow();

    // the ledger records the activity after the task completes
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var records = readFlow(flowId);

              assertThat(
                      records.stream()
                          .filter(record -> record instanceof InteractionRecord)
                          .map(record -> ((InteractionRecord) record).agentComponentId())
                          .distinct())
                  .contains("coordinator-agent", "worker-agent");

              var events =
                  records.stream()
                      .filter(record -> record instanceof ActivityRecord)
                      .flatMap(record -> ((ActivityRecord) record).events().stream())
                      .toList();

              var delegationStarted =
                  events.stream()
                      .filter(event -> event instanceof ActivityRecord.Event.DelegationStarted)
                      .map(event -> (ActivityRecord.Event.DelegationStarted) event)
                      .findFirst()
                      .orElseThrow();
              assertThat(delegationStarted.workerComponentIds()).containsExactly("worker-agent");
              assertThat(delegationStarted.subtaskIds()).hasSize(1);

              var delegationResolved =
                  events.stream()
                      .filter(event -> event instanceof ActivityRecord.Event.DelegationResolved)
                      .map(event -> (ActivityRecord.Event.DelegationResolved) event)
                      .findFirst()
                      .orElseThrow();
              assertThat(delegationResolved.succeeded()).isEqualTo(1);
              assertThat(delegationResolved.failed()).isZero();
              assertThat(delegationResolved.succeededSubtaskIds())
                  .containsExactlyElementsOf(delegationStarted.subtaskIds());

              assertThat(events)
                  .anySatisfy(
                      event ->
                          assertThat(event)
                              .isInstanceOfSatisfying(
                                  ActivityRecord.Event.TaskCompleted.class,
                                  completed -> assertThat(completed.taskId()).isEqualTo(taskId)));

              assertThat(FlowRecord.transcript(records)).contains("Research quantum computing");
            });
  }

  private List<FlowRecord> readFlow(String flowId) {
    var page = getLedgerClient().getFlowRecords(flowId);
    var records = new ArrayList<FlowRecord>(page.records());
    while (page.nextPosition().isPresent()) {
      page = getLedgerClient().getFlowRecords(flowId, page.nextPosition().get());
      records.addAll(page.records());
    }
    return records;
  }
}
