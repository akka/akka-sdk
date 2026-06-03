/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.CANCEL_UNCLAIMED_TASKS_FROM_BACKLOG;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.CLAIM_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.COMPLETE_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.CREATE_TEAM;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.DISBAND_TEAM;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.GET_BACKLOG_STATUS;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.GET_MANAGED_BACKLOG_STATUS;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.GET_TEAM_STATUS;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.RELEASE_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.SEND_MESSAGE;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.TRANSFER_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.cancelUnclaimedTasksFromBacklog;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.claimTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTaskForBacklog;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTaskForBacklogToolName;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTeam;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.disbandTeam;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.extractContacts;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.getBacklogStatus;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.getManagedBacklogStatus;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.getTeamStatus;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.releaseTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.sendMessage;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.transferTask;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.TeamMemberSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for team management and backlog tools: createTeam, getTeamStatus, disbandTeam,
 * createTaskForBacklog, getManagedBacklogStatus, cancelUnclaimedTasksFromBacklog, getBacklogStatus,
 * claimTask, releaseTask, transferTask.
 */
public class TeamBacklogIntegrationTest extends TestKitSupport {

  private final TestModelProvider leadModel =
      new TestModelProvider().withMessageSelector(TeamBacklogIntegrationTest::preferToolResult);
  private final TestModelProvider workerModel =
      new TestModelProvider().withMessageSelector(TeamBacklogIntegrationTest::preferToolResult);

  private static TestModelProvider.InputMessage preferToolResult(
      List<TestModelProvider.InputMessage> messages) {
    return messages.stream()
        .filter(m -> m instanceof TestModelProvider.ToolResult)
        .reduce((a, b) -> b)
        .orElse(messages.getLast());
  }

  private static final String WORK_ITEM_TASK_TOOL =
      createTaskForBacklogToolName(TestTasks.WORK_ITEM);

  // Matches an available task in a get_backlog_status listing, e.g.
  //   - Task 6f8c4ef3-... (Work Item): [available] '...'
  private static final Pattern TASK_ID_PATTERN =
      Pattern.compile("Task\\s+([0-9a-f-]{36})\\s*\\([^)]*\\):\\s*\\[available]");

  // Matches the task id echoed back by a claim_task result, e.g. "Claimed and assigned task <id>".
  private static final Pattern CLAIMED_TASK_ID_PATTERN =
      Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(TeamLeadAgent.class, leadModel)
        .withModelProvider(TeamWorkerAgent.class, workerModel);
  }

  @AfterEach
  public void afterEach() {
    leadModel.reset();
    // Re-apply message selector after reset
    leadModel.withMessageSelector(TeamBacklogIntegrationTest::preferToolResult);
    workerModel.reset();
    workerModel.withMessageSelector(TeamBacklogIntegrationTest::preferToolResult);
  }

  @Test
  public void shouldCreateTeamAndDisband() {
    // Lead: createTeam -> getTeamStatus -> disbandTeam -> completeTask
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM -> new AiResponse(getTeamStatus());
              case GET_TEAM_STATUS -> new AiResponse(disbandTeam());
              case DISBAND_TEAM ->
                  new AiResponse(
                      completeTask(new TestTasks.PlanResult("Team created and disbanded.", 0)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    workerModel.fixedResponse("Ready for work.");

    var leadId = UUID.randomUUID().toString();
    var leadClient = componentClient.forAutonomousAgent(TeamLeadAgent.class, leadId);

    var leadNotifications = new ArrayList<Notification>();
    leadClient.notificationStream().runForeach(leadNotifications::add, testKit.getMaterializer());

    var taskId =
        leadClient.runSingleTask(TestTasks.PLAN.instructions("Create and disband a team."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary())
                  .contains("Team created and disbanded");
            });

    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var teamCreated =
                  leadNotifications.stream()
                      .filter(n -> n instanceof Notification.TeamCreated)
                      .map(n -> (Notification.TeamCreated) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(teamCreated.teamId()).isNotBlank();
              assertThat(teamCreated.memberComponentIds()).containsExactly("team-worker-agent");
              assertThat(teamCreated.memberInstanceIds()).hasSize(1);

              assertThat(leadNotifications)
                  .anySatisfy(n -> assertThat(n).isInstanceOf(Notification.TeamMemberReady.class));

              var teamDisbanded =
                  leadNotifications.stream()
                      .filter(n -> n instanceof Notification.TeamDisbanded)
                      .map(n -> (Notification.TeamDisbanded) n)
                      .findFirst()
                      .orElseThrow();
              assertThat(teamDisbanded.teamId()).isEqualTo(teamCreated.teamId());
            });
  }

  @Test
  public void shouldManageBacklogTasks() {
    // Lead: createTeam -> createTaskForBacklog (Task) -> getManagedBacklogStatus ->
    //       cancelUnclaimedTasksFromBacklog -> disbandTeam -> completeTask
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM ->
                  new AiResponse(
                      createTaskForBacklog(
                          TestTasks.WORK_ITEM.instructions("Do something"), "Do something"));
              case String s when s.equals(WORK_ITEM_TASK_TOOL) ->
                  new AiResponse(getManagedBacklogStatus());
              case GET_MANAGED_BACKLOG_STATUS -> new AiResponse(cancelUnclaimedTasksFromBacklog());
              case CANCEL_UNCLAIMED_TASKS_FROM_BACKLOG -> new AiResponse(disbandTeam());
              case DISBAND_TEAM ->
                  new AiResponse(completeTask(new TestTasks.PlanResult("Backlog managed.", 0)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    workerModel.fixedResponse("Ready for work.");

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Manage backlog tasks."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary()).contains("Backlog managed");
            });
  }

  @Test
  public void shouldCreateBacklogTaskWithTemplateParams() {
    // Lead: createTeam -> createTaskForBacklog (TaskTemplate with params) ->
    //       getManagedBacklogStatus -> disbandTeam -> completeTask
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM ->
                  new AiResponse(
                      createTaskForBacklog(
                          TestTasks.WORK_ITEM,
                          Map.of("item", "Auth module", "requirements", "OAuth support")));
              case String s when s.equals(WORK_ITEM_TASK_TOOL) ->
                  new AiResponse(getManagedBacklogStatus());
              case GET_MANAGED_BACKLOG_STATUS -> new AiResponse(disbandTeam());
              case DISBAND_TEAM ->
                  new AiResponse(
                      completeTask(new TestTasks.PlanResult("Template task created.", 0)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    workerModel.fixedResponse("Ready for work.");

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Create backlog task with template."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary())
                  .contains("Template task created");
            });
  }

  @Test
  public void shouldClaimAndCompleteBacklogTask() {
    // Lead: createTeam -> createTaskForBacklog -> poll getManagedBacklogStatus until completed ->
    //       disbandTeam -> completeTask
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM ->
                  new AiResponse(
                      createTaskForBacklog(
                          TestTasks.WORK_ITEM,
                          Map.of("item", "Feature X", "requirements", "Build it")));
              case String s when s.equals(WORK_ITEM_TASK_TOOL) ->
                  new AiResponse(getManagedBacklogStatus());
              case GET_MANAGED_BACKLOG_STATUS -> {
                if (toolResult.content().contains("completed")) {
                  yield new AiResponse(disbandTeam());
                }
                yield new AiResponse(getManagedBacklogStatus());
              }
              case DISBAND_TEAM ->
                  new AiResponse(completeTask(new TestTasks.PlanResult("Work completed.", 1)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    // Worker: getBacklogStatus -> claimTask -> completeTask
    workerModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case GET_BACKLOG_STATUS -> {
                var matcher = TASK_ID_PATTERN.matcher(toolResult.content());
                if (matcher.find()) {
                  yield new AiResponse(claimTask(matcher.group(1)));
                }
                yield new AiResponse(getBacklogStatus());
              }
              case CLAIM_TASK ->
                  new AiResponse(
                      completeTask(new TestTasks.WorkItemResult("Feature X", "Implemented.")));
              case COMPLETE_TASK -> new AiResponse(getBacklogStatus());
              default -> new AiResponse(getBacklogStatus());
            };
          }
          return new AiResponse(getBacklogStatus());
        });

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Coordinate team to build Feature X."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().tasksCompleted()).isEqualTo(1);
            });
  }

  @Test
  public void shouldReleaseBacklogTask() {
    // Worker: getBacklogStatus -> claimTask -> releaseTask -> completeTask
    // Claiming dispatches the task to task processing immediately, so releasing only clears the
    // backlog claim while the worker keeps processing the task. The worker exercises release_task
    // and then completes the task it is still working on.
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM ->
                  new AiResponse(
                      createTaskForBacklog(
                          TestTasks.WORK_ITEM,
                          Map.of("item", "Releasable task", "requirements", "Test release")));
              case String s when s.equals(WORK_ITEM_TASK_TOOL) ->
                  new AiResponse(getManagedBacklogStatus());
              case GET_MANAGED_BACKLOG_STATUS -> {
                if (toolResult.content().contains("completed")) {
                  yield new AiResponse(disbandTeam());
                }
                yield new AiResponse(getManagedBacklogStatus());
              }
              case DISBAND_TEAM ->
                  new AiResponse(completeTask(new TestTasks.PlanResult("Release tested.", 1)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    workerModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case GET_BACKLOG_STATUS -> {
                var matcher = TASK_ID_PATTERN.matcher(toolResult.content());
                if (matcher.find()) {
                  yield new AiResponse(claimTask(matcher.group(1)));
                }
                yield new AiResponse(getBacklogStatus());
              }
              case CLAIM_TASK -> {
                // Exercise release_task on the freshly claimed task.
                var matcher = CLAIMED_TASK_ID_PATTERN.matcher(toolResult.content());
                if (matcher.find()) {
                  yield new AiResponse(releaseTask(matcher.group(1)));
                }
                yield new AiResponse(
                    completeTask(
                        new TestTasks.WorkItemResult("Releasable task", "Done after release.")));
              }
              // Releasing the claim does not stop processing, so complete the task afterwards.
              case RELEASE_TASK ->
                  new AiResponse(
                      completeTask(
                          new TestTasks.WorkItemResult("Releasable task", "Done after release.")));
              case COMPLETE_TASK -> new AiResponse(getBacklogStatus());
              default -> new AiResponse(getBacklogStatus());
            };
          }
          return new AiResponse(getBacklogStatus());
        });

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Test task release."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary()).contains("Release tested");
            });
  }

  @Test
  public void shouldTransferBacklogTask() {
    // Worker: getBacklogStatus -> claimTask -> transferTask -> completeTask (own task)
    leadModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.UserMessage) {
            return new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class, 2)));
          }
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case CREATE_TEAM ->
                  new AiResponse(
                      createTaskForBacklog(
                          TestTasks.WORK_ITEM,
                          Map.of("item", "Transferable task", "requirements", "Test transfer")));
              case String s when s.equals(WORK_ITEM_TASK_TOOL) ->
                  new AiResponse(getManagedBacklogStatus());
              case GET_MANAGED_BACKLOG_STATUS -> {
                if (toolResult.content().contains("completed")) {
                  yield new AiResponse(disbandTeam());
                }
                yield new AiResponse(getManagedBacklogStatus());
              }
              case DISBAND_TEAM ->
                  new AiResponse(completeTask(new TestTasks.PlanResult("Transfer tested.", 1)));
              default -> new AiResponse("Continuing work.");
            };
          }
          return new AiResponse("Continuing work.");
        });

    workerModel.fixedResponse(
        msg -> {
          if (msg instanceof TestModelProvider.ToolResult toolResult) {
            return switch (toolResult.name()) {
              case GET_BACKLOG_STATUS -> {
                var matcher = TASK_ID_PATTERN.matcher(toolResult.content());
                if (matcher.find()) {
                  yield new AiResponse(claimTask(matcher.group(1)));
                }
                yield new AiResponse(getBacklogStatus());
              }
              case CLAIM_TASK -> {
                // Transfer to another agent instance
                var matcher = CLAIMED_TASK_ID_PATTERN.matcher(toolResult.content());
                if (matcher.find()) {
                  yield new AiResponse(
                      transferTask(matcher.group(1), "team-worker-agent/other-instance"));
                }
                yield new AiResponse(
                    completeTask(
                        new TestTasks.WorkItemResult(
                            "Transferable task", "Completed after transfer.")));
              }
              case TRANSFER_TASK ->
                  new AiResponse(
                      completeTask(
                          new TestTasks.WorkItemResult(
                              "Transferable task", "Completed after transfer.")));
              case COMPLETE_TASK -> new AiResponse(getBacklogStatus());
              default -> new AiResponse(getBacklogStatus());
            };
          }
          return new AiResponse(getBacklogStatus());
        });

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Test task transfer."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(60, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary()).contains("Transfer tested");
            });
  }

  @Test
  public void shouldSendMessageBetweenTeamMembers() {
    // Lead: createTeam -> send_message to member -> getManagedBacklogStatus -> disbandTeam ->
    // completeTask
    leadModel.fixedResponse(
        msg -> {
          return switch (msg) {
            case TestModelProvider.UserMessage um -> {
              var contacts = extractContacts(um);
              if (!contacts.isEmpty()) {
                // After team setup, send a message to the first member
                yield new AiResponse(sendMessage(contacts.get(0), "Welcome to the team!"));
              }
              // First user message triggers team creation
              yield new AiResponse(createTeam(new TeamMemberSpec(TeamWorkerAgent.class)));
            }
            case TestModelProvider.ToolResult toolResult ->
                switch (toolResult.name()) {
                  case CREATE_TEAM -> new AiResponse(getTeamStatus());
                  case GET_TEAM_STATUS -> new AiResponse(getManagedBacklogStatus());
                  case SEND_MESSAGE -> new AiResponse(getManagedBacklogStatus());
                  case GET_MANAGED_BACKLOG_STATUS -> new AiResponse(disbandTeam());
                  case DISBAND_TEAM ->
                      new AiResponse(
                          completeTask(new TestTasks.PlanResult("Message sent to member.", 0)));
                  default -> new AiResponse("Continuing work.");
                };
          };
        });

    workerModel.fixedResponse("Ready for work.");

    var taskId =
        componentClient
            .forAutonomousAgent(TeamLeadAgent.class, UUID.randomUUID().toString())
            .runSingleTask(TestTasks.PLAN.instructions("Send a message to a team member."));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(taskId).get(TestTasks.PLAN);
              assertThat(snapshot.result().orElseThrow().summary()).contains("Message sent");
            });
  }
}
