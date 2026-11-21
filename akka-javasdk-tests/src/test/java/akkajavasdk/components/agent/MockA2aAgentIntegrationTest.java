/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.MockA2aAgent.Reply;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** An agent calls the TestKit's mock of a remote A2A agent, which is named in the settings. */
public class MockA2aAgentIntegrationTest extends TestKitSupport {

  private static final MockA2aAgent claims =
      MockA2aAgent.create("claims")
          .withSkill(
              "check-claim", "Checks whether a claim is valid", Reply.text("claim 42 is valid"))
          .withSkill("pay", "Pays a claim", Reply.failed("not covered"))
          .start();

  private final TestModelProvider model = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(RemoteA2aToolTestAgent.class, model)
        .withMockedA2aAgent("claims", claims);
  }

  @AfterEach
  public void afterEach() {
    model.reset();
    claims.reset();
  }

  @AfterAll
  public static void stop() {
    claims.close();
  }

  @Test
  public void shouldCallTheMockedSkillAndRecordTheMessage() {
    model
        .whenMessage(message -> message.equals("Is claim 42 valid?"))
        .reply(new ToolInvocationRequest("claims_check-claim", "{\"message\":\"check claim 42\"}"));
    model
        .whenToolResult(result -> result.name().equals("claims_check-claim"))
        .thenReply(result -> new AiResponse("Answer: " + result.content()));
    var sessionId = UUID.randomUUID().toString();

    var reply =
        componentClient
            .forAgent()
            .inSession(sessionId)
            .method(RemoteA2aToolTestAgent::ask)
            .invoke("Is claim 42 valid?");

    assertThat(reply).isEqualTo("Answer: claim 42 is valid");
    assertThat(claims.received())
        .singleElement()
        .satisfies(
            received -> {
              assertThat(received.skillId()).isEqualTo("check-claim");
              assertThat(received.text()).isEqualTo("check claim 42");
              assertThat(received.contextId()).isEqualTo(sessionId);
            });
  }
}
