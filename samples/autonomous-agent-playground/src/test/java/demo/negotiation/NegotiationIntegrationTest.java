package demo.negotiation;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.COMPLETE_TASK;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.DIRECT;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.END_CONVERSATION;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.START_CONVERSATION;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.SUBMIT_TURN;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.direct;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.endConversation;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.startDirectedConversation;
import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.submitTurn;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.ParticipantRef;
import demo.negotiation.api.NegotiationEndpoint;
import demo.negotiation.application.Buyer;
import demo.negotiation.application.Facilitator;
import demo.negotiation.application.NegotiationTasks;
import demo.negotiation.application.NegotiationTasks.NegotiationResult;
import demo.negotiation.application.Seller;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class NegotiationIntegrationTest extends TestKitSupport {

  private final TestModelProvider facilitatorModel = new TestModelProvider()
    .withMessageSelector(NegotiationIntegrationTest::preferToolResult);
  private final TestModelProvider buyerModel = new TestModelProvider()
    .withMessageSelector(NegotiationIntegrationTest::preferToolResult);
  private final TestModelProvider sellerModel = new TestModelProvider()
    .withMessageSelector(NegotiationIntegrationTest::preferToolResult);

  private static TestModelProvider.InputMessage preferToolResult(
    List<TestModelProvider.InputMessage> messages
  ) {
    return messages
      .stream()
      .filter(m -> m instanceof TestModelProvider.ToolResult)
      .reduce((a, b) -> b)
      .orElse(messages.getLast());
  }

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    )
      .withModelProvider(Facilitator.class, facilitatorModel)
      .withModelProvider(Buyer.class, buyerModel)
      .withModelProvider(Seller.class, sellerModel);
  }

  @Test
  public void shouldRunDirectedNegotiation() {
    // Facilitator: drive negotiation through directed tool calls.
    // Uses preferToolResult — each tool result triggers the next action.
    facilitatorModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.UserMessage) {
        // Task assigned — start directed conversation
        return new AiResponse(
          startDirectedConversation(
            "Software licensing deal",
            5,
            List.of(new ParticipantRef("buyer"), new ParticipantRef("seller"))
          )
        );
      }
      if (msg instanceof TestModelProvider.ToolResult toolResult) {
        return switch (toolResult.name()) {
          case START_CONVERSATION -> new AiResponse(
            direct("buyer", "Make your opening offer for the license.")
          );
          case DIRECT -> {
            if (toolResult.content().contains("[buyer]")) {
              yield new AiResponse(direct("seller", "Respond to the buyer's offer."));
            }
            // Seller responded — end negotiation
            yield new AiResponse(endConversation());
          }
          case END_CONVERSATION -> new AiResponse(
            completeTask(
              new NegotiationResult(
                "Software licensing deal",
                "Agreement reached on licensing terms.",
                "$50,000 annual license"
              )
            )
          );
          case COMPLETE_TASK -> new AiResponse("Done.");
          default -> new AiResponse("Acknowledged.");
        };
      }
      return new AiResponse("Acknowledged.");
    });

    // Participants: submit turn whenever given the floor
    buyerModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(submitTurn("We offer $50,000 for an annual license."));
    });

    sellerModel.fixedResponse(msg -> {
      if (msg instanceof TestModelProvider.ToolResult tr && tr.name().equals(SUBMIT_TURN)) {
        return new AiResponse("Turn submitted.");
      }
      return new AiResponse(submitTurn("We accept $50,000 for the annual license."));
    });

    var response = httpClient
      .POST("/negotiation")
      .withRequestBody(
        new NegotiationEndpoint.NegotiationRequest("Negotiate software licensing terms")
      )
      .responseBodyAs(NegotiationEndpoint.NegotiationResponse.class)
      .invoke()
      .body();

    var taskId = response.taskId();
    assertThat(taskId).isNotBlank();

    Awaitility.await()
      .ignoreExceptions()
      .atMost(30, TimeUnit.SECONDS)
      .untilAsserted(() -> {
        var snapshot = componentClient.forTask(taskId).get(NegotiationTasks.NEGOTIATE);
        var result = snapshot.result().orElseThrow();
        assertThat(result.topic()).isEqualTo("Software licensing deal");
        assertThat(result.outcome()).contains("Agreement reached");
        assertThat(result.finalOffer()).isEqualTo("$50,000 annual license");
      });
  }
}
