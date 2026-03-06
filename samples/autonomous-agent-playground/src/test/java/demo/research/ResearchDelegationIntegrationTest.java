package demo.research;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.research.api.ResearchEndpoint;
import demo.research.application.Analyst;
import demo.research.application.ResearchCoordinator;
import demo.research.application.ResearchTasks;
import demo.research.application.Researcher;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ResearchDelegationIntegrationTest extends TestKitSupport {

  private final TestModelProvider coordinatorModel = new TestModelProvider();
  private final TestModelProvider researcherModel = new TestModelProvider();
  private final TestModelProvider analystModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(ResearchCoordinator.class, coordinatorModel)
      .withModelProvider(Researcher.class, researcherModel)
      .withModelProvider(Analyst.class, analystModel);
  }

  @Test
  public void shouldDelegateToWorkersAndSynthesizeResult() {
    // Coordinator's first LLM call: delegate to both workers
    coordinatorModel
      .whenMessage(msg -> msg.contains("quantum computing"))
      .reply(
        List.of(
          new TestModelProvider.ToolInvocationRequest(
            "delegate_to_researcher",
            "{\"instructions\":\"Research quantum computing fundamentals\"}"
          ),
          new TestModelProvider.ToolInvocationRequest(
            "delegate_to_analyst",
            "{\"instructions\":\"Analyse quantum computing market trends\"}"
          )
        )
      );

    // Researcher completes with ResearchFindings
    researcherModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"topic\":\"Quantum Computing\"," +
          "\"facts\":[\"Qubits enable parallel computation\",\"Error correction is advancing\"]," +
          "\"sources\":[\"Nature Physics 2024\",\"IBM Research\"]}"
        )
      )
    );

    // Analyst completes with AnalysisReport
    analystModel.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"topic\":\"Quantum Computing\"," +
          "\"assessment\":\"Market growing rapidly with key players investing heavily.\"," +
          "\"trends\":[\"$50B market by 2030\",\"Cloud quantum access expanding\"]}"
        )
      )
    );

    // Coordinator's second LLM call: after receiving delegation results, synthesize
    coordinatorModel
      .whenToolResult(tr -> tr.name().startsWith("delegate_"))
      .reply(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"title\":\"Quantum Computing Brief\"," +
          "\"summary\":\"Quantum computing leverages qubits for parallel computation " +
          "with a rapidly growing market projected at $50B by 2030.\"," +
          "\"keyFindings\":[\"Qubits enable parallel computation\",\"Error correction is advancing\"," +
          "\"$50B market by 2030\",\"Cloud quantum access expanding\"]}"
        )
      );

    var response = httpClient
      .POST("/research")
      .withRequestBody(new ResearchEndpoint.ResearchRequest("quantum computing"))
      .responseBodyAs(ResearchEndpoint.ResearchResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(ResearchTasks.BRIEF).get(taskId);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().title()).isEqualTo("Quantum Computing Brief");
        assertThat(snapshot.result().keyFindings()).hasSize(4);
      });
  }
}
