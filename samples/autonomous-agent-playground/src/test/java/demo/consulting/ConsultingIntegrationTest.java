package demo.consulting;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.consulting.api.ConsultingEndpoint.EngagementRequest;
import demo.consulting.api.ConsultingEndpoint.EngagementResponse;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingResearcher;
import demo.consulting.application.ConsultingTasks;
import demo.consulting.application.SeniorConsultant;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsultingIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider researcherModel = new TestModelProvider();
  private final TestModelProvider seniorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withAdditionalConfig("akka.javasdk.agent.openai.api-key = n/a")
        .withModelProvider(ConsultingCoordinator.class, coordinatorModel)
        .withModelProvider(ConsultingResearcher.class, researcherModel)
        .withModelProvider(SeniorConsultant.class, seniorModel);
  }

  @Test
  public void shouldDelegateStandardProblem() {
    // Coordinator: assess problem
    coordinatorModel
        .whenMessage(msg -> msg.contains("market analysis"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "ConsultingTools_assessProblem",
                "{\"problemDescription\":\"market analysis for Southeast Asia expansion\"}"
            )
        );

    // Coordinator: check complexity after assessment
    coordinatorModel
        .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "ConsultingTools_checkComplexity",
                "{\"assessment\":\"market analysis, standard scope\"}"
            )
        );

    // Coordinator: delegate research after complexity check
    coordinatorModel
        .whenToolResult(tr -> tr.name().equals("ConsultingTools_checkComplexity"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "delegate_to_consulting_researcher",
                "{\"instructions\":\"Research market conditions for Southeast Asia expansion\"}"
            )
        );

    // Researcher: complete research task
    researcherModel.fixedResponse(
        new TestModelProvider.AiResponse(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"topic\":\"Southeast Asia market expansion\",\"findings\":\"Growing market with strong consumer demand and favorable demographics.\"}"
            )
        )
    );

    // Coordinator: synthesise after delegation result
    coordinatorModel
        .whenToolResult(tr -> tr.name().startsWith("delegate_"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"assessment\":\"Standard market analysis problem\",\"recommendation\":\"Proceed with phased expansion based on favorable demographics.\",\"escalated\":false}"
            )
        );

    var response = httpClient
        .POST("/engagements")
        .withRequestBody(new EngagementRequest("market analysis for Southeast Asia expansion"))
        .responseBodyAs(EngagementResponse.class)
        .invoke();

    var taskId = response.body().taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var snapshot = componentClient.forTask(ConsultingTasks.ENGAGEMENT).get(taskId);
          assertThat(snapshot.result()).isNotNull();
          assertThat(snapshot.result().escalated()).isFalse();
          assertThat(snapshot.result().recommendation()).contains("phased expansion");
        });
  }

  @Test
  public void shouldHandoffComplexProblem() {
    // Coordinator: assess problem
    coordinatorModel
        .whenMessage(msg -> msg.contains("regulatory"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "ConsultingTools_assessProblem",
                "{\"problemDescription\":\"regulatory compliance for GDPR\"}"
            )
        );

    // Coordinator: check complexity
    coordinatorModel
        .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "ConsultingTools_checkComplexity",
                "{\"assessment\":\"regulatory compliance for GDPR\"}"
            )
        );

    // Coordinator: handoff after complexity check
    coordinatorModel
        .whenToolResult(tr -> tr.name().equals("ConsultingTools_checkComplexity"))
        .reply(
            new TestModelProvider.ToolInvocationRequest(
                "handoff_to_senior_consultant",
                "{\"context\":\"Complex regulatory problem involving GDPR compliance.\"}"
            )
        );

    // Senior consultant: complete engagement
    seniorModel.fixedResponse(
        new TestModelProvider.AiResponse(
            new TestModelProvider.ToolInvocationRequest(
                "complete_task",
                "{\"assessment\":\"Complex GDPR regulatory compliance issue\",\"recommendation\":\"Implement data processing agreements and conduct DPIA.\",\"escalated\":true}"
            )
        )
    );

    var response = httpClient
        .POST("/engagements")
        .withRequestBody(new EngagementRequest("regulatory compliance for GDPR data processing"))
        .responseBodyAs(EngagementResponse.class)
        .invoke();

    var taskId = response.body().taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
        .ignoreExceptions()
        .atMost(30, TimeUnit.SECONDS)
        .untilAsserted(() -> {
          var snapshot = componentClient.forTask(ConsultingTasks.ENGAGEMENT).get(taskId);
          assertThat(snapshot.result()).isNotNull();
          assertThat(snapshot.result().escalated()).isTrue();
          assertThat(snapshot.result().recommendation()).contains("data processing agreements");
        });
  }

  @Test
  public void shouldRejectEmptyProblem() {
    var exception = org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> httpClient
            .POST("/engagements")
            .withRequestBody(new EngagementRequest(""))
            .responseBodyAs(String.class)
            .invoke());

    assertThat(exception.getMessage()).contains("400");
  }

  @Test
  public void shouldRejectUnknownTaskId() {
    var exception = org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> httpClient
            .GET("/engagements/nonexistent-task-id")
            .responseBodyAs(String.class)
            .invoke());

    assertThat(exception.getMessage()).contains("400");
  }
}
