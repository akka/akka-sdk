package com.example.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

public class ClaimStatusAgentIntegrationTest extends TestKitSupport {

  // tag::mock[]
  private static final MockA2aAgent claims = MockA2aAgent.create("claims")
    .withSkill(
      "claim-status",
      "Looks up the status of a claim",
      MockA2aAgent.Reply.text("Claim 42 is approved")
    ) // <1>
    .start(); // <2>

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    var settings = TestKit.Settings.DEFAULT.withModelProvider(ClaimStatusAgent.class, model); // <3>
    return settings.withMockedA2aAgent("claims", claims); // <4>
  }

  // end::mock[]

  @AfterAll
  public static void stopMock() {
    claims.close();
  }

  // tag::test[]
  @Test
  public void answersFromTheRemoteAgent() {
    model
      .whenMessage(message -> message.equals("What is the status of claim 42?"))
      .reply(
        new ToolInvocationRequest(
          "claims_claim-status", // <1>
          "{\"message\":\"Status of claim 42\"}"
        )
      );
    model
      .whenToolResult(result -> result.name().equals("claims_claim-status"))
      .thenReply(result -> new AiResponse("The remote agent says: " + result.content()));

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(ClaimStatusAgent::ask)
      .invoke("What is the status of claim 42?");

    assertThat(reply).isEqualTo("The remote agent says: Claim 42 is approved");
    assertThat(claims.received()) // <2>
      .singleElement()
      .satisfies(received -> assertThat(received.text()).isEqualTo("Status of claim 42"));
  }
  // end::test[]
}
