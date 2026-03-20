package demo.devteam;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.devteam.api.DevTeamEndpoint;
import demo.devteam.application.Developer;
import demo.devteam.application.ProjectLead;
import demo.devteam.application.ProjectTasks;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DevTeamIntegrationTest extends TestKitSupport {

  private final TestModelProvider leadModel = new TestModelProvider();
  private final TestModelProvider developerModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(ProjectLead.class, leadModel)
        .withModelProvider(Developer.class, developerModel);
  }

  @Test
  public void shouldFormTeamAndCoordinateWork() {
    // Lead iteration 1: add a developer to the team
    leadModel
        .whenMessage(msg -> msg.contains("Plan and execute a project"))
        .reply(
            new TestModelProvider.ToolInvocationRequest("add_developer_to_team", "{}"));

    // Lead iteration 2: complete the project
    leadModel
        .whenMessage(msg -> msg.contains("Continue working"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"summary\":\"Project delivered with auth module.\","
                    + "\"deliverables\":[\"auth module\"]}"));

    // Developer: complete task when assigned
    developerModel.fixedResponse(
        new TestModelProvider.AiResponse(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"feature\":\"auth\","
                    + "\"implementation\":\"OAuth2 authentication module\","
                    + "\"tests\":\"All auth tests passing\"}")));

    var response =
        httpClient
            .POST("/devteam")
            .withRequestBody(
                new DevTeamEndpoint.ProjectRequest("Build an authentication system"))
            .responseBodyAs(DevTeamEndpoint.ProjectResponse.class)
            .invoke()
            .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var snapshot = componentClient.forTask(ProjectTasks.PLAN).get(taskId);
              assertThat(snapshot.result()).isNotNull();
              assertThat(snapshot.result().summary()).contains("Project delivered");
              assertThat(snapshot.result().deliverables()).contains("auth module");
            });
  }
}
