/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.ToolCallGuardrail;
import akka.javasdk.testkit.MockA2aAgent;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akka.stream.javadsl.Sink;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The service configures credentials for the agent `claims` in application.conf. The mock replaces
 * them, so every test here reaches the plain HTTP mock.
 */
public class A2aAgentIntegrationTest extends TestKitSupport {

  public static final List<String> received = new CopyOnWriteArrayList<>();

  public static final MockA2aAgent CLAIMS =
      MockA2aAgent.named("claims")
          .withSkill(
              "lookup-claim",
              "Looks up a claim by id",
              message -> {
                received.add(message);
                if (message.contains("boom")) throw new IllegalStateException("claim store down");
                return "Claim 42 is open";
              })
          .withSkill("close-claim", "Closes a claim", message -> "closed");

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(A2aClaimsTestAgent.class, testModelProvider)
        .withModelProvider(A2aClaimsDeniedTestAgent.class, testModelProvider)
        .withModelProvider(A2aClaimsLegacyGuardedTestAgent.class, testModelProvider)
        .withModelProvider(A2aClaimsStreamingTestAgent.class, testModelProvider)
        .withMockedA2aAgent(CLAIMS);
  }

  @AfterEach
  public void afterEach() {
    testModelProvider.reset();
    received.clear();
    RecordingAllowingToolGuard.lastCall.set(null);
    RecordingDenyingToolGuard.lastCall.set(null);
  }

  private static String sessionId() {
    return UUID.randomUUID().toString();
  }

  private A2aClaimsTestAgent.SomeResponse ask(String question) {
    return componentClient
        .forAgent()
        .inSession(sessionId())
        .method(A2aClaimsTestAgent::ask)
        .invoke(question);
  }

  private void modelCallsLookupSkill(String question, String message) {
    testModelProvider
        .whenMessage(s -> s.equals(question))
        .reply(
            new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"" + message + "\"}"));
    testModelProvider
        .whenToolResult(result -> true)
        .thenReply(result -> new AiResponse("error=" + result.isError() + " " + result.content()));
  }

  @Test
  public void shouldCallSkillOfMockedA2aAgent() {
    modelCallsLookupSkill("status of claim 42", "claim 42");

    var response = ask("status of claim 42");

    assertThat(response.response()).isEqualTo("error=false Claim 42 is open");
    assertThat(received).containsExactly("claim 42");
  }

  @Test
  public void shouldGiveModelErrorResultWhenSkillHandlerThrows() {
    modelCallsLookupSkill("break it", "boom");

    var response = ask("break it");

    assertThat(response.response()).startsWith("error=true");
    assertThat(response.response()).contains("claim store down");
  }

  @Test
  public void shouldOnlyOfferTheAllowedSkills() {
    // the allowed skill works ...
    modelCallsLookupSkill("status of claim 42", "claim 42");
    assertThat(ask("status of claim 42").response()).isEqualTo("error=false Claim 42 is open");
    testModelProvider.reset();
    received.clear();

    // ... and the skill that the filter drops does not exist for the model
    testModelProvider
        .whenMessage(s -> s.equals("close it"))
        .reply(new ToolInvocationRequest("claims__close-claim", "{\"message\":\"claim 42\"}"));

    assertThatThrownBy(() -> ask("close it")).hasMessageContaining("Unknown tool name");
    assertThat(received).isEmpty();
  }

  @Test
  public void shouldEvaluateToolCallGuardrailOnTheSkillWithTheRemoteA2aOrigin() {
    modelCallsLookupSkill("status of claim 42", "claim 42");

    ask("status of claim 42");

    // the guardrail of the skill id ran, and the one of another skill id did not
    var recorded = RecordingAllowingToolGuard.lastCall.get();
    assertThat(recorded).isNotNull();
    assertThat(recorded.toolName()).isEqualTo("lookup-claim");
    assertThat(recorded.arguments()).contains("claim 42");
    assertThat(recorded.origin())
        .isEqualTo(new ToolCallGuardrail.ToolOrigin.RemoteA2a("claims", "lookup-claim"));
    assertThat(RecordingDenyingToolGuard.lastCall.get()).isNull();
  }

  @Test
  public void shouldDenySkillCallWithToolCallGuardrail() {
    testModelProvider
        .whenMessage(s -> s.equals("status of claim 42"))
        .reply(new ToolInvocationRequest("claims__lookup-claim", "{\"message\":\"claim 42\"}"));

    var response =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .method(A2aClaimsDeniedTestAgent::ask)
            .invoke("status of claim 42");

    assertThat(response.response()).contains("denied by a2a tool guard [lookup-claim]");
    assertThat(received).isEmpty();
    var recorded = RecordingDenyingToolGuard.lastCall.get();
    assertThat(recorded).isNotNull();
    assertThat(recorded.origin())
        .isEqualTo(new ToolCallGuardrail.ToolOrigin.RemoteA2a("claims", "lookup-claim"));
  }

  @Test
  public void shouldOfferTheSkillsToAgentWithLegacyModelGuardrail() {
    modelCallsLookupSkill("status of claim 42", "claim 42");

    var response =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .method(A2aClaimsLegacyGuardedTestAgent::ask)
            .invoke("status of claim 42");

    assertThat(response.response()).isEqualTo("error=false Claim 42 is open");
    assertThat(received).containsExactly("claim 42");
  }

  @Test
  public void shouldOfferTheSkillsToStreamingAgent() throws Exception {
    modelCallsLookupSkill("status of claim 42", "claim 42");

    var tokens =
        componentClient
            .forAgent()
            .inSession(sessionId())
            .tokenStream(A2aClaimsStreamingTestAgent::ask)
            .source("status of claim 42")
            .runWith(Sink.seq(), testKit.getMaterializer())
            .toCompletableFuture()
            .get(10, TimeUnit.SECONDS);

    assertThat(String.join("", tokens)).isEqualTo("error=false Claim 42 is open");
    assertThat(received).containsExactly("claim 42");
  }
}
