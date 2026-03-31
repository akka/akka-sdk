package demo.devteam;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.CLAIM_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.COMPLETE_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.CREATE_TEAM;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.DISBAND_TEAM;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.GET_BACKLOG_STATUS;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.GET_MANAGED_BACKLOG_STATUS;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.claimTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTaskForBacklog;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTaskForBacklogToolName;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.createTeam;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.disbandTeam;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.getBacklogStatus;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.getManagedBacklogStatus;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.TeamMemberSpec;
import demo.devteam.api.DevTeamEndpoint;
import demo.devteam.application.CodeDeliverable;
import demo.devteam.application.Developer;
import demo.devteam.application.DeveloperTasks;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DevTeamIntegrationTest extends TestKitSupport {

  private final TestModelProvider leadModel = new TestModelProvider()
    .withMessageSelector(DevTeamIntegrationTest::preferToolResult);
  private final TestModelProvider developerModel = new TestModelProvider()
    .withMessageSelector(DevTeamIntegrationTest::preferToolResult);

  /** Select the last tool result if present, otherwise the last message. */
  private static TestModelProvider.InputMessage preferToolResult(
    List<TestModelProvider.InputMessage> messages
  ) {
    return messages
      .stream()
      .filter(m -> m instanceof TestModelProvider.ToolResult)
      .reduce((a, b) -> b)
      .orElse(messages.getLast());
  }

  private static final String IMPLEMENT_TASK_TOOL = createTaskForBacklogToolName(
    DeveloperTasks.IMPLEMENT
  );

  private static final Pattern TASK_ID_PATTERN = Pattern.compile(
    "\"task_id\"\\s*:\\s*\"([^\"]+)\""
  );

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ProjectLead.class, leadModel)
      .withModelProvider(Developer.class, developerModel);
  }

  /** Configure the developer model to react to backlog messages. */
  private void setupDeveloperModel(Object taskResult) {
    developerModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage) {
        return new AiResponse("Ready for work.");
      }
      if (msg instanceof TestModelProvider.ToolResult toolResult) {
        return switch (toolResult.name()) {
          case GET_BACKLOG_STATUS -> {
            var matcher = TASK_ID_PATTERN.matcher(toolResult.content());
            if (matcher.find()) {
              yield new AiResponse(claimTask(matcher.group(1)));
            }
            yield new AiResponse(getBacklogStatus());
          }
          case CLAIM_TASK -> new AiResponse(completeTask(taskResult));
          case COMPLETE_TASK -> new AiResponse(getBacklogStatus());
          default -> new AiResponse(getBacklogStatus());
        };
      }
      return new AiResponse(getBacklogStatus());
    });
  }

  @Test
  public void shouldFormTeamAndCoordinateWork() {
    // Lead: react to tool results to drive the team workflow
    leadModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage) {
        return new AiResponse(createTeam(new TeamMemberSpec(Developer.class)));
      }
      if (msg instanceof TestModelProvider.ToolResult toolResult) {
        return switch (toolResult.name()) {
          case CREATE_TEAM -> new AiResponse(
            createTaskForBacklog(
              DeveloperTasks.IMPLEMENT,
              Map.of("feature", "auth", "requirements", "Build authentication module")
            )
          );
          case String s when s.equals(IMPLEMENT_TASK_TOOL) -> new AiResponse(
            getManagedBacklogStatus()
          );
          case GET_MANAGED_BACKLOG_STATUS -> {
            if (toolResult.content().contains("completed")) {
              yield new AiResponse(disbandTeam());
            }
            yield new AiResponse(getManagedBacklogStatus());
          }
          case DISBAND_TEAM -> new AiResponse(
            completeTask(
              new ProjectTasks.ProjectResult(
                "Project delivered with auth module.",
                List.of("auth module")
              )
            )
          );
          default -> new AiResponse("Continuing work.");
        };
      }
      return new AiResponse("Continuing work.");
    });

    setupDeveloperModel(
      new CodeDeliverable("auth", "OAuth2 authentication module", "All auth tests passing")
    );

    var response = httpClient
      .POST("/devteam")
      .withRequestBody(new DevTeamEndpoint.ProjectRequest("Build an authentication system"))
      .responseBodyAs(DevTeamEndpoint.ProjectResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(ProjectTasks.PLAN);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().summary()).contains("Project delivered");
        assertThat(snapshot.result().deliverables()).contains("auth module");
      });
  }

  @Test
  public void shouldRecreateTeamForSecondPhase() {
    var teamsCreated = new AtomicInteger(0);

    // Lead: two-phase workflow — create team, add task, wait, disband, repeat, then complete
    leadModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage) {
        // Initial task assignment — create first team
        teamsCreated.incrementAndGet();
        return new AiResponse(createTeam(new TeamMemberSpec(Developer.class)));
      }
      if (msg instanceof TestModelProvider.ToolResult toolResult) {
        return switch (toolResult.name()) {
          case CREATE_TEAM -> {
            var params = teamsCreated.get() == 1
              ? Map.of("feature", "API", "requirements", "Build the API layer")
              : Map.of("feature", "tests", "requirements", "Build the test suite");
            yield new AiResponse(createTaskForBacklog(DeveloperTasks.IMPLEMENT, params));
          }
          case String s when s.equals(IMPLEMENT_TASK_TOOL) -> new AiResponse(
            getManagedBacklogStatus()
          );
          case GET_MANAGED_BACKLOG_STATUS -> {
            if (toolResult.content().contains("completed")) {
              yield new AiResponse(disbandTeam());
            }
            yield new AiResponse(getManagedBacklogStatus());
          }
          case DISBAND_TEAM -> {
            if (teamsCreated.get() < 2) {
              // First phase done — start second phase
              teamsCreated.incrementAndGet();
              yield new AiResponse(createTeam(new TeamMemberSpec(Developer.class)));
            }
            // Both phases done — complete the project
            yield new AiResponse(
              completeTask(
                new ProjectTasks.ProjectResult(
                  "Project delivered in two phases.",
                  List.of("API layer", "test suite")
                )
              )
            );
          }
          default -> new AiResponse("Continuing work.");
        };
      }
      return new AiResponse("Continuing work.");
    });

    setupDeveloperModel(
      new CodeDeliverable("implementation", "Completed implementation", "All tests passing")
    );

    var response = httpClient
      .POST("/devteam")
      .withRequestBody(new DevTeamEndpoint.ProjectRequest("Build and test an API"))
      .responseBodyAs(DevTeamEndpoint.ProjectResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(60, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(ProjectTasks.PLAN);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().summary()).contains("two phases");
        assertThat(snapshot.result().deliverables()).contains("API layer", "test suite");
      });
  }
}
