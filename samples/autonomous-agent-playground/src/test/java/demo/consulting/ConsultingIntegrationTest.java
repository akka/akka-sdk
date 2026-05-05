package demo.consulting;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.delegateTo;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.handoffTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.consulting.api.ConsultingEndpoint;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingResearcher;
import demo.consulting.application.ConsultingTasks;
import demo.consulting.application.ConsultingTasks.ConsultingResult;
import demo.consulting.application.ConsultingTasks.ResearchSummary;
import demo.consulting.application.FactCheckAgent;
import demo.consulting.application.SeniorConsultant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ConsultingIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider researcherModel = new TestModelProvider();
  private final TestModelProvider seniorModel = new TestModelProvider();
  private final TestModelProvider factCheckModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ConsultingCoordinator.class, coordinatorModel)
      .withModelProvider(ConsultingResearcher.class, researcherModel)
      .withModelProvider(SeniorConsultant.class, seniorModel)
      .withModelProvider(FactCheckAgent.class, factCheckModel);
  }

  @Test
  public void shouldDelegateResearchForStandardProblem() {
    // tag::tool-use[]
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
        delegateTo(
          ConsultingTasks.RESEARCH,
          ConsultingResearcher.class,
          "Research supply chain optimization best practices"
        )
      );
    // end::tool-use[]

    // Researcher completes
    researcherModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ResearchSummary(
            "Supply chain optimization",
            "Key practices: demand forecasting, supplier diversification, " +
            "inventory automation."
          )
        )
      )
    );

    // Coordinator synthesises after delegation result
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        completeTask(
          new ConsultingResult(
            "Standard supply chain problem",
            "Implement demand forecasting and supplier diversification.",
            false
          )
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
        var snapshot = componentClient.forTask(taskId).get(ConsultingTasks.ENGAGEMENT);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().escalated()).isFalse();
        assertThat(snapshot.result().recommendation()).contains("demand forecasting");
      });
  }

  @Test
  public void shouldDelegateToRequestBasedFactCheckAgent() {
    // Coordinator: assess → delegate fact-check to request-based agent → synthesise
    coordinatorModel
      .whenMessage(msg -> msg.contains("climate impact"))
      .reply(
        List.of(
          new TestModelProvider.ToolInvocationRequest(
            "ConsultingTools_assessProblem",
            "{\"problemDescription\":\"climate impact of proposed changes\"}"
          )
        )
      );

    coordinatorModel
      .whenToolResult(tr -> tr.name().equals("ConsultingTools_assessProblem"))
      .reply(
        delegateTo(
          FactCheckAgent.class,
          "{\"claim\":\"The proposed changes will reduce carbon emissions by 40%\"}"
        )
      );

    // Fact-check agent responds
    factCheckModel.fixedResponse(
      "{\"verified\":true,\"confidence\":75," +
      "\"explanation\":\"Industry data supports 30-45% reduction range.\"}"
    );

    // Coordinator synthesises after fact-check result
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        completeTask(
          new ConsultingResult(
            "Climate impact claim verified with moderate confidence",
            "Proceed with changes; claims are broadly supported by data.",
            false
          )
        )
      );

    var response = httpClient
      .POST("/consulting")
      .withRequestBody(
        new ConsultingEndpoint.ConsultingRequest(
          "Verify the climate impact claims of our proposed changes"
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
        var snapshot = componentClient.forTask(taskId).get(ConsultingTasks.ENGAGEMENT);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().escalated()).isFalse();
        assertThat(snapshot.result().recommendation()).contains("broadly supported");
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
        handoffTo(
          SeniorConsultant.class,
          "Complex problem involving regulatory compliance for merger. " +
          "Preliminary assessment indicates M&A and regulatory scope."
        )
      );

    // Senior consultant resolves
    seniorModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ConsultingResult(
            "Complex regulatory merger problem",
            "Engage regulatory counsel, conduct due diligence, " +
            "establish compliance framework before proceeding.",
            true
          )
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
        var snapshot = componentClient.forTask(taskId).get(ConsultingTasks.ENGAGEMENT);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().escalated()).isTrue();
        assertThat(snapshot.result().recommendation()).contains("regulatory counsel");
      });
  }
}
