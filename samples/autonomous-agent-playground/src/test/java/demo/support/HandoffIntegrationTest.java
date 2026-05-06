package demo.support;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.handoffTo;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.support.api.SupportEndpoint;
import demo.support.application.BillingSpecialist;
import demo.support.application.SupportTasks;
import demo.support.application.SupportTasks.SupportResolution;
import demo.support.application.TechnicalSpecialist;
import demo.support.application.TriageAgent;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class HandoffIntegrationTest extends TestKitSupport {

  private final TestModelProvider triageModel = new TestModelProvider();
  private final TestModelProvider billingModel = new TestModelProvider();
  private final TestModelProvider technicalModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(TriageAgent.class, triageModel)
      .withModelProvider(BillingSpecialist.class, billingModel)
      .withModelProvider(TechnicalSpecialist.class, technicalModel);
  }

  // tag::handoff[]
  @Test
  public void shouldHandoffToBillingSpecialist() {
    // Triage agent classifies as billing and hands off
    triageModel.fixedResponse(
      new TestModelProvider.AiResponse(
        handoffTo(
          BillingSpecialist.class,
          "Customer has a billing dispute about double charge on invoice #1234."
        )
      )
    );

    // Billing specialist resolves the issue
    billingModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new SupportResolution(
            "billing",
            "Refund issued for duplicate charge on invoice #1234.",
            true
          )
        )
      )
    );

    var response = httpClient
      .POST("/support")
      .withRequestBody(
        new SupportEndpoint.SupportRequest(
          "I was charged twice on invoice #1234, please fix this."
        )
      )
      .responseBodyAs(SupportEndpoint.SupportResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().category()).isEqualTo("billing");
        assertThat(snapshot.result().resolved()).isTrue();
      });
  }

  // end::handoff[]

  @Test
  public void shouldHandoffToTechnicalSpecialist() {
    // Triage agent classifies as technical and hands off
    triageModel.fixedResponse(
      new TestModelProvider.AiResponse(
        handoffTo(
          TechnicalSpecialist.class,
          "Customer reports service outage affecting their dashboard."
        )
      )
    );

    // Technical specialist resolves the issue
    technicalModel.fixedResponse(
      new TestModelProvider.AiResponse(
        completeTask(
          new SupportResolution(
            "technical",
            "Dashboard service restarted and cache cleared.",
            true
          )
        )
      )
    );

    var response = httpClient
      .POST("/support")
      .withRequestBody(
        new SupportEndpoint.SupportRequest("My dashboard is down, I can't see any data.")
      )
      .responseBodyAs(SupportEndpoint.SupportResponse.class)
      .invoke()
      .body();

    var taskId = response.id();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(SupportTasks.RESOLVE);
        assertThat(snapshot.result()).isNotNull();
        assertThat(snapshot.result().category()).isEqualTo("technical");
        assertThat(snapshot.result().resolved()).isTrue();
      });
  }
}
