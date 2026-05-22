package demo.research;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.*;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.research.api.ResearchEndpoint;
import demo.research.application.AnalysisReport;
import demo.research.application.Analyst;
import demo.research.application.ResearchBrief;
import demo.research.application.ResearchCoordinator;
import demo.research.application.ResearchFindings;
import demo.research.application.ResearchTasks;
import demo.research.application.Researcher;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

// tag::setup[]
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

  // end::setup[]

  // tag::delegation-test[]
  @Test
  public void shouldDelegateToWorkersAndSynthesizeResult() {
    // Coordinator delegates to both workers
    coordinatorModel
      .whenMessage(msg -> msg.contains("quantum computing"))
      .reply(
        List.of(
          delegateTo(
            ResearchTasks.FINDINGS,
            Researcher.class,
            "Research quantum computing fundamentals"
          ),
          delegateTo(
            ResearchTasks.ANALYSIS,
            Analyst.class,
            "Analyse quantum computing market trends"
          )
        )
      );

    // Researcher completes with ResearchFindings
    researcherModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new ResearchFindings(
            "Quantum Computing",
            List.of("Qubits enable parallel computation", "Error correction is advancing"),
            List.of("Nature Physics 2024", "IBM Research")
          )
        )
      )
    );

    // Analyst completes with AnalysisReport
    analystModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new AnalysisReport(
            "Quantum Computing",
            "Market growing rapidly with key players investing heavily.",
            List.of("$50B market by 2030", "Cloud quantum access expanding")
          )
        )
      )
    );

    // Coordinator synthesizes after both workers complete
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        completeTask(
          new ResearchBrief(
            "Quantum Computing Brief",
            "Quantum computing leverages qubits for parallel computation with a rapidly growing market projected at $50B by 2030.",
            List.of(
              "Qubits enable parallel computation",
              "Error correction is advancing",
              "$50B market by 2030",
              "Cloud quantum access expanding"
            )
          )
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
        var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
        var result = snapshot.result().orElseThrow();
        assertThat(result.title()).isEqualTo("Quantum Computing Brief");
        assertThat(result.keyFindings()).hasSize(4);
      });
  }

  // end::delegation-test[]

  @Test
  public void shouldRetryFindingsAfterSourcesRejection() {
    // Coordinator delegates to both workers
    coordinatorModel
      .whenMessage(msg -> msg.contains("quantum computing"))
      .reply(
        List.of(
          delegateTo(
            ResearchTasks.FINDINGS,
            Researcher.class,
            "Research quantum computing fundamentals"
          ),
          delegateTo(
            ResearchTasks.ANALYSIS,
            Analyst.class,
            "Analyse quantum computing market trends"
          )
        )
      );

    // Researcher first attempt: no sources — rule will reject this
    researcherModel
      .whenMessage(msg -> msg.contains("Research quantum computing"))
      .reply(
        completeTask(
          new ResearchFindings(
            "Quantum Computing",
            List.of("Qubits enable parallel computation"),
            List.of() // no sources — triggers rule rejection
          )
        )
      );

    // Researcher retries with sources after rejection (runtime sends iteration reminder)
    researcherModel
      .whenMessage(msg -> msg.contains("Reminder"))
      .reply(
        completeTask(
          new ResearchFindings(
            "Quantum Computing",
            List.of("Qubits enable parallel computation", "Error correction is advancing"),
            List.of("Nature Physics 2024", "IBM Research")
          )
        )
      );

    // Analyst completes normally
    analystModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new AnalysisReport(
            "Quantum Computing",
            "Market growing rapidly with key players investing heavily.",
            List.of("$50B market by 2030", "Cloud quantum access expanding")
          )
        )
      )
    );

    // Coordinator synthesizes after both workers complete
    coordinatorModel
      .whenMessage(msg -> msg.contains("Continue working"))
      .reply(
        completeTask(
          new ResearchBrief(
            "Quantum Computing Brief",
            "Quantum computing leverages qubits for parallel computation with a rapidly growing market projected at $50B by 2030.",
            List.of(
              "Qubits enable parallel computation",
              "Error correction is advancing",
              "$50B market by 2030",
              "Cloud quantum access expanding"
            )
          )
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
        var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
        var result = snapshot.result().orElseThrow();
        assertThat(result.title()).isEqualTo("Quantum Computing Brief");
        assertThat(result.keyFindings()).hasSize(4);
      });
  }
}
