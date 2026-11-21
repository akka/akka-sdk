package claims;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.ContentTypes;
import akka.javasdk.JsonSupport;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import claims.application.ClaimStatusAgent;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ClaimsA2aEndpointIntegrationTest extends TestKitSupport {

  // The runtime prefixes the name of a tool with the simple name of the class that defines it.
  private static final String CLAIM_STATUS_TOOL = "ClaimStatusAgent_claimStatus";

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(ClaimStatusAgent.class, model);
  }

  @Test
  public void servesAnAgentCardWithBothSkills() throws Exception {
    var response = httpClient.GET("/.well-known/agent-card.json").invoke();

    var card = JsonSupport.getObjectMapper().readTree(response.body().utf8String());
    assertThat(card.get("skills"))
      .extracting(skill -> skill.get("id").asText())
      .containsExactlyInAnyOrder("claim-status", "review-claim");
  }

  @Test
  public void answersTheShortSkillWithTheAgent() throws Exception {
    model
      .whenMessage(message -> message.contains("claim 42"))
      .reply(new ToolInvocationRequest(CLAIM_STATUS_TOOL, "{\"claimNumber\":\"42\"}"));
    model
      .whenToolResult(result -> result.name().equals(CLAIM_STATUS_TOOL))
      .thenReply(result -> new AiResponse("Claim 42 is " + result.content() + "."));

    var task = send("claim-status", "What is the status of claim 42?", null);

    assertThat(task.at("/status/state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(task.at("/artifacts/0/parts/0/text").asText()).isEqualTo(
      "Claim 42 is approved, payment on its way."
    );
  }

  @Test
  public void asksTheLongSkillForThePolicyNumberAndCompletes() throws Exception {
    var first = send("review-claim", "Review claim 42, water damage in the kitchen", null);
    assertThat(first.at("/status/state").asText()).isEqualTo("TASK_STATE_INPUT_REQUIRED");

    var last = send("review-claim", "P-1234", first.get("id").asText());

    assertThat(last.at("/status/state").asText()).isEqualTo("TASK_STATE_COMPLETED");
    assertThat(last.at("/artifacts/0/parts/0/data/policyNumber").asText()).isEqualTo(
      "P-1234"
    );
  }

  @Test
  public void completesTheLongSkillAtOnceWhenTheMessageHasThePolicyNumber() throws Exception {
    var task = send("review-claim", "Review claim 43 under policy P-77", null);

    assertThat(task.at("/status/state").asText()).isEqualTo("TASK_STATE_COMPLETED");
  }

  private JsonNode send(String skillId, String text, String taskId) throws Exception {
    var message = new LinkedHashMap<String, Object>();
    message.put("messageId", UUID.randomUUID().toString());
    message.put("role", "ROLE_USER");
    message.put("parts", List.of(Map.of("text", text)));
    message.put("metadata", Map.of("skillId", skillId));
    if (taskId != null) message.put("taskId", taskId);
    var request = Map.of(
      "jsonrpc",
      "2.0",
      "id",
      1,
      "method",
      "SendMessage",
      "params",
      Map.of("message", message)
    );
    var response = httpClient
      .POST("/a2a")
      .withRequestBody(
        ContentTypes.APPLICATION_JSON,
        JsonSupport.getObjectMapper().writeValueAsBytes(request)
      )
      .invoke();
    return JsonSupport.getObjectMapper()
      .readTree(response.body().utf8String())
      .get("result")
      .get("task");
  }
}
