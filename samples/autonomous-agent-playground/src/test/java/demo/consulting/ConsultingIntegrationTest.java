package demo.consulting;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.consulting.api.ConsultingEndpoint;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingResearcher;
import demo.consulting.application.ConsultingTasks;
import demo.consulting.application.SeniorConsultant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ConsultingIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider researcherModel = new TestModelProvider();
  private final TestModelProvider seniorModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ConsultingCoordinator.class, coordinatorModel)
      .withModelProvider(ConsultingResearcher.class, researcherModel)
      .withModelProvider(SeniorConsultant.class, seniorModel);
  }

  @Test
  public void shouldDelegateResearchForStandardProblem() {
    // Coordinator: assess → check complexity → delegate research → synthesise
    coordinatorModel
      .whenMessage(msg -> msg.contains("supply chain"))
      .reply(
        List.of(
          new TestModelProvider.ToolInvocationRequest(
            "ConsultingTools_assessProblem",
            "{\"problemDescription\":\"supply chain efficiency\"}"
          )
        )
      );

    coordinatorModel
      .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "ConsultingTools_checkComplexity",
          "{\"assessment\":\"moderate complexity, integration challenges\"}"
        )
      );

    coordinatorModel
      .whenToolResult(tr -> tr.name().equals("ConsultingTools_checkComplexity"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "delegate_to_consulting_researcher",
          "{\"instructions\":\"Research supply chain optimization best practices\"}"
        )
      );

    // Researcher completes
    researcherModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"topic\":\"Supply chain optimization\"," +
          "\"findings\":\"Key practices: demand forecasting, supplier diversification, " +
          "inventory automation.\"}"
        )
      )
    );

    // Coordinator synthesises after delegation result
    coordinatorModel
      .whenToolResult(tr -> tr.name().startsWith("delegate_"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"assessment\":\"Standard supply chain problem\"," +
          "\"recommendation\":\"Implement demand forecasting and supplier diversification.\"," +
          "\"escalated\":false}"
        )
      );

    var response = httpClient
      .POST("/consulting")
      .withRequestBody(
        new ConsultingEndpoint.ConsultingRequest("How to improve our supply chain efficiency")
      )
      .responseBodyAs(ConsultingEndpoint.ConsultingResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(ConsultingTasks.ENGAGEMENT).get(taskId);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().escalated()).isFalse();
        assertThat(snapshot.result().recommendation()).contains("demand forecasting");
      });
  }

  @Test
  public void shouldHandoffToSeniorConsultantForComplexProblem() {
    // Coordinator: assess → check complexity → handoff to senior
    coordinatorModel
      .whenMessage(msg -> msg.contains("merger"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "ConsultingTools_assessProblem",
          "{\"problemDescription\":\"regulatory compliance for merger\"}"
        )
      );

    coordinatorModel
      .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "ConsultingTools_checkComplexity",
          "{\"assessment\":\"regulatory and merger considerations\"}"
        )
      );

    coordinatorModel
      .whenToolResult(tr -> tr.name().equals("ConsultingTools_checkComplexity"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "handoff_to_senior_consultant",
          "{\"context\":\"Complex problem involving regulatory compliance for merger. " +
          "Preliminary assessment indicates M&A and regulatory scope.\"}"
        )
      );

    // Senior consultant resolves
    seniorModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"assessment\":\"Complex regulatory merger problem\"," +
          "\"recommendation\":\"Engage regulatory counsel, conduct due diligence, " +
          "establish compliance framework before proceeding.\"," +
          "\"escalated\":true}"
        )
      )
    );

    var response = httpClient
      .POST("/consulting")
      .withRequestBody(
        new ConsultingEndpoint.ConsultingRequest(
          "Regulatory compliance for our upcoming merger"
        )
      )
      .responseBodyAs(ConsultingEndpoint.ConsultingResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(ConsultingTasks.ENGAGEMENT).get(taskId);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().escalated()).isTrue();
        assertThat(snapshot.result().recommendation()).contains("regulatory counsel");
      });
  }
}
