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
  private final TestModelProvider requestDelegatingModel = new TestModelProvider();
  private final TestModelProvider someAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(CoordinatorAgent.class, coordinatorModel)
        .withModelProvider(WorkerAgent.class, workerModel)
        .withModelProvider(RequestDelegatingAgent.class, requestDelegatingModel)
        .withModelProvider(SomeAgent.class, someAgentModel);
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

    var flowId = flowIdOf("coordinator-agent", coordinatorId);

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

              var events = activityEvents(records);

              var delegationStarted =
                  firstEvent(events, ActivityRecord.Event.DelegationStarted.class);
              assertThat(delegationStarted.workerComponentIds()).containsExactly("worker-agent");
              assertThat(delegationStarted.subtaskIds()).hasSize(1);

              var delegationResolved =
                  firstEvent(events, ActivityRecord.Event.DelegationResolved.class);
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

  @Test
  public void readsTheWorkerOfADelegationToARequestBasedAgent() {
    requestDelegatingModel
        .whenMessage(msg -> msg.contains("verify"))
        .reply(delegateTo(SomeAgent.class, "{\"claim\":\"The sky is blue.\"}"));

    someAgentModel.fixedResponse("{\"response\":\"Verified: the sky is indeed blue.\"}");

    requestDelegatingModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(completeTask(new TestTasks.TestResult("Claim verified.", 90)));

    var agentId = UUID.randomUUID().toString();
    var taskId =
        componentClient
            .forAutonomousAgent(RequestDelegatingAgent.class, agentId)
            .runSingleTask(TestTasks.TEST_TASK.instructions("Please verify: the sky is blue."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.TEST_TASK).result())
                    .isPresent());

    var flowId = flowIdOf("request-delegating-agent", agentId);

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var records = readFlow(flowId);

              var delegationStarted =
                  firstEvent(activityEvents(records), ActivityRecord.Event.DelegationStarted.class);
              assertThat(delegationStarted.workerComponentIds()).containsExactly("some-agent");
              assertThat(delegationStarted.workerInstanceIds()).containsExactly("");
              assertThat(delegationStarted.subtaskIds()).isEmpty();
              assertThat(delegationStarted.delegationCount()).isEqualTo(1);

              assertThat(FlowRecord.transcript(records))
                  .contains("started 1 delegation to [some-agent]");
            });
  }

  // the session of an autonomous agent interaction is the id of the agent instance
  private String flowIdOf(String agentComponentId, String agentInstanceId) {
    return Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .until(
            () ->
                getLedgerClient().getInteractions(agentComponentId, agentInstanceId).stream()
                    .flatMap(interaction -> interaction.flowId().stream())
                    .findFirst(),
            Optional::isPresent)
        .orElseThrow();
  }

  private static List<ActivityRecord.Event> activityEvents(List<FlowRecord> records) {
    return records.stream()
        .filter(record -> record instanceof ActivityRecord)
        .flatMap(record -> ((ActivityRecord) record).events().stream())
        .toList();
  }

  private static <T extends ActivityRecord.Event> T firstEvent(
      List<ActivityRecord.Event> events, Class<T> type) {
    return events.stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
  }

  private List<FlowRecord> readFlow(String flowId) {
    var page = getLedgerClient().getFlowRecords(flowId);
    var records = new ArrayList<FlowRecord>(page.records());
    while (page.hasMore()) {
      page = getLedgerClient().getFlowRecords(flowId, page.nextPosition());
      records.addAll(page.records());
    }
    return records;
  }
}
