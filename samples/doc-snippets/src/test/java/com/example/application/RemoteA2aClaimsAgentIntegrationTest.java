package com.example.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class RemoteA2aClaimsAgentIntegrationTest extends TestKitSupport {

  private final TestModelProvider model = new TestModelProvider();

  // tag::mock-a2a-agent[]
  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
      .withModelProvider(RemoteA2aClaimsAgent.class, model)
      .withMockedA2aAgent( // <1>
        MockA2aAgent.named("claims") // <2>
          .withSkill(
            "lookup-claim", // <3>
            "Looks up a claim by id",
            message -> "Claim 42 is open" // <4>
          )
      );
  }

  // end::mock-a2a-agent[]

  @Test
  public void shouldAnswerWithTheResultOfTheSkill() {
    model
      .whenMessage(message -> message.equals("What is the status of claim 42?"))
      .reply(new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"claim 42\"}"));
    model
      .whenToolResult(result -> true)
      .thenReply(result -> new AiResponse("{\"answer\":\"" + result.content() + "\"}"));

    var response = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(RemoteA2aClaimsAgent::query)
      .invoke("What is the status of claim 42?");

    assertEquals("Claim 42 is open", response.answer());
  }
}
