package demo.dynamic;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import demo.dynamic.api.DynamicEndpoint;
import demo.dynamic.application.DynamicAgent;
import demo.dynamic.application.DynamicTasks;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class DynamicSetupIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(DynamicAgent.class, model);
  }

  @Test
  public void shouldSummarizeWithDynamicSetup() {
    model.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"result\":\"This is a summary of the content.\"}"
        )
      )
    );

    var response = httpClient
      .POST("/dynamic/summarize")
      .withRequestBody(new DynamicEndpoint.TaskRequest("Some long content to summarize."))
      .responseBodyAs(DynamicEndpoint.TaskResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(DynamicTasks.SUMMARIZE);
        assertThat(snapshot.result()).isEqualTo("This is a summary of the content.");
      });
  }

  @Test
  public void shouldTranslateWithDynamicSetup() {
    model.fixedResponse(
      new TestModelProvider.AiResponse(
        new TestModelProvider.ToolInvocationRequest(
          "complete_task",
          "{\"result\":\"Ceci est le contenu traduit.\"}"
        )
      )
    );

    var response = httpClient
      .POST("/dynamic/translate")
      .withRequestBody(new DynamicEndpoint.TaskRequest("This is the content to translate."))
      .responseBodyAs(DynamicEndpoint.TaskResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(10, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(DynamicTasks.TRANSLATE);
        assertThat(snapshot.result()).isEqualTo("Ceci est le contenu traduit.");
      });
  }
}
