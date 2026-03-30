package demo.devteam;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.devteam.api.DevTeamEndpoint;
import demo.devteam.application.Developer;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DevTeamIntegrationTest extends TestKitSupport {

  private final TestModelProvider leadModel = new TestModelProvider();
  private final TestModelProvider developerModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ProjectLead.class, leadModel)
      .withModelProvider(Developer.class, developerModel);
  }

  @Test
  public void shouldFormTeamAndCoordinateWork() {
    // Lead iteration 1: create a team with one developer
    leadModel
      .whenMessage(msg -> msg.contains("Plan and execute a project"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "create_team",
          "{\"members\":[{\"type\":\"developer\",\"count\":1}]}"
        )
      );

    // Lead iteration 2: complete the project
    leadModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"summary\":\"Project delivered with auth module.\"," +
          "\"deliverables\":[\"auth module\"]}"
        )
      );

    // Developer: complete task when assigned
    developerModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"feature\":\"auth\"," +
          "\"implementation\":\"OAuth2 authentication module\"," +
          "\"tests\":\"All auth tests passing\"}"
        )
      )
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
    var leadStep = new AtomicInteger(0);

    // Lead steps: create -> add items -> disband -> create -> add -> disband -> complete
    // The runtime sends UserMessage prompts, so we sequence via whenMessage with a counter.
    // Each step is registered as a separate predicate that matches exactly once.
    var steps = List.<TestModelProvider.ToolInvocationRequest>of(
      // step 0: initial task assignment — create first team
      new TestModelProvider.ToolInvocationRequest(
        "create_team",
        "{\"members\":[{\"type\":\"developer\",\"count\":1}]}"
      ),
      // step 1: add backlog item for phase 1
      new TestModelProvider.ToolInvocationRequest(
        "add_backlog_item",
        "{\"name\":\"Implement\",\"description\":\"Build the API layer\"}"
      ),
      // step 2: check team status (give developer time to complete)
      new TestModelProvider.ToolInvocationRequest(
        "get_team_status",
        "{}"
      ),
      // step 3: disband first team
      new TestModelProvider.ToolInvocationRequest(
        "disband_team",
        "{}"
      ),
      // step 4: create second team for phase 2
      new TestModelProvider.ToolInvocationRequest(
        "create_team",
        "{\"members\":[{\"type\":\"developer\",\"count\":1}]}"
      ),
      // step 5: add backlog item for phase 2
      new TestModelProvider.ToolInvocationRequest(
        "add_backlog_item",
        "{\"name\":\"Implement\",\"description\":\"Build the test suite\"}"
      ),
      // step 6: check second team status
      new TestModelProvider.ToolInvocationRequest(
        "get_team_status",
        "{}"
      ),
      // step 7: disband second team
      new TestModelProvider.ToolInvocationRequest(
        "disband_team",
        "{}"
      ),
      // step 8: complete the project
      new TestModelProvider.ToolInvocationRequest(
        "complete_task",
        "{\"summary\":\"Project delivered in two phases.\"," +
        "\"deliverables\":[\"API layer\",\"test suite\"]}"
      )
    );

    // Use replyWith for lazy evaluation — advances through steps on each lead model call
    leadModel
      .whenMessage(msg -> true)
      .replyWith(msg -> new TestModelProvider.AiResponse(
        steps.get(Math.min(leadStep.getAndIncrement(), steps.size() - 1))
      ));

    // Developer: always complete assigned tasks
    developerModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"feature\":\"implementation\"," +
          "\"implementation\":\"Completed implementation\"," +
          "\"tests\":\"All tests passing\"}"
        )
      )
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
