/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class A2aAgentIntegrationTest extends TestKitSupport {

  private static final List<String> received = new CopyOnWriteArrayList<>();

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(A2aClaimsTestAgent.class, testModelProvider)
        .withMockedA2aAgent(
            MockA2aAgent.named("claims")
                .withSkill(
                    "lookup-claim",
                    "Looks up a claim by id",
                    message -> {
                      received.add(message);
                      if (message.contains("boom"))
                        throw new IllegalStateException("claim store down");
                      return "Claim 42 is open";
                    })
                .withSkill("close-claim", "Closes a claim", message -> "closed"));
  }

  @AfterEach
  public void afterEach() {
    testModelProvider.reset();
    received.clear();
  }

  private A2aClaimsTestAgent.SomeResponse ask(String question) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(A2aClaimsTestAgent::ask)
        .invoke(question);
  }

  @Test
  public void shouldCallSkillOfMockedA2aAgent() {
    testModelProvider
        .whenMessage(s -> s.equals("status of claim 42"))
        .reply(new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"claim 42\"}"));
    testModelProvider
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse("Model saw: " + result.content()));

    var response = ask("status of claim 42");

    assertThat(response.response()).isEqualTo("Model saw: Claim 42 is open");
    assertThat(received).containsExactly("claim 42");
  }

  @Test
  public void shouldGiveModelErrorResultWhenSkillHandlerThrows() {
    testModelProvider
        .whenMessage(s -> s.equals("break it"))
        .reply(new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"boom\"}"));
    testModelProvider
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse("error=" + result.isError() + " " + result.content()));

    var response = ask("break it");

    assertThat(response.response()).startsWith("error=true");
    assertThat(response.response()).contains("claim store down");
  }

  @Test
  public void shouldNotOfferSkillsThatAreNotAllowed() {
    testModelProvider
        .whenMessage(s -> s.equals("close it"))
        .reply(new ToolInvocationRequest("claims__close-claim", "{\"message\":\"claim 42\"}"));

    assertThatThrownBy(() -> ask("close it")).hasMessageContaining("Unknown tool name");
    assertThat(received).isEmpty();
  }
}
