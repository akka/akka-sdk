/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.agent.autonomous.SanitizerAutonomousTestAgent;
import akkajavasdk.components.agent.autonomous.TestTasks;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Covers which agent and which application point each configured sanitizer masks at, for the
 * sanitizers in the test application.conf.
 */
@ExtendWith(Junit5LogCapturing.class)
public class SanitizerIntegrationTest extends TestKitSupport {

  private final TestModelProvider scopedAgentModel = new TestModelProvider();
  private final TestModelProvider otherAgentModel = new TestModelProvider();
  private final TestModelProvider roleAgentModel = new TestModelProvider();
  private final TestModelProvider autonomousAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(SanitizerTestAgent.class, scopedAgentModel)
        .withModelProvider(SanitizerOtherTestAgent.class, otherAgentModel)
        .withModelProvider(SanitizerRoleTestAgent.class, roleAgentModel)
        .withModelProvider(SanitizerAutonomousTestAgent.class, autonomousAgentModel);
  }

  @AfterEach
  public void afterEach() {
    scopedAgentModel.reset();
    otherAgentModel.reset();
    roleAgentModel.reset();
    autonomousAgentModel.reset();
    RecordingSanitizer.reset();
  }

  private static String newSessionId() {
    return UUID.randomUUID().toString();
  }

  /** Captures the text the model was asked about, after the runtime masked it. */
  private AtomicReference<String> captureUserMessage(TestModelProvider model) {
    var captured = new AtomicReference<String>();
    model.fixedResponse(
        message -> {
          captured.set(message.content());
          return new AiResponse("done");
        });
    return captured;
  }

  private void askScopedAgent(String question) {
    componentClient
        .forAgent()
        .inSession(newSessionId())
        .method(SanitizerTestAgent::query)
        .invoke(question);
  }

  private void askOtherAgent(String question) {
    componentClient
        .forAgent()
        .inSession(newSessionId())
        .method(SanitizerOtherTestAgent::query)
        .invoke(question);
  }

  private void askRoleAgent(String question) {
    componentClient
        .forAgent()
        .inSession(newSessionId())
        .method(SanitizerRoleTestAgent::query)
        .invoke(question);
  }

  @Test
  public void shouldMaskForTheAgentTheSanitizerNamesAndNoOther() {
    var scopedCapture = captureUserMessage(scopedAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);

    askScopedAgent("tell me about scopedsecret");
    askOtherAgent("tell me about scopedsecret");

    assertThat(scopedCapture.get()).contains("tell me about ************");
    assertThat(otherCapture.get()).contains("tell me about scopedsecret");
  }

  @Test
  public void shouldMaskForEveryAgentWhenTheSanitizerNamesNone() {
    var scopedCapture = captureUserMessage(scopedAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);
    var roleCapture = captureUserMessage(roleAgentModel);

    askScopedAgent("tell me about modelsecret");
    askOtherAgent("tell me about modelsecret");
    askRoleAgent("tell me about modelsecret");

    assertThat(scopedCapture.get()).contains("tell me about ***********");
    assertThat(otherCapture.get()).contains("tell me about ***********");
    assertThat(roleCapture.get()).contains("tell me about ***********");
  }

  @Test
  public void shouldMaskForTheRoleTheSanitizerNamesAndNoOther() {
    var roleCapture = captureUserMessage(roleAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);

    askRoleAgent("tell me about rolesecret");
    askOtherAgent("tell me about rolesecret");

    assertThat(roleCapture.get()).contains("tell me about **********");
    assertThat(otherCapture.get()).contains("tell me about rolesecret");
  }

  @Test
  public void shouldMaskForAnAutonomousAgentWithTheRoleTheSanitizerNames() {
    var captured = new AtomicReference<String>();
    autonomousAgentModel.fixedResponse(
        message -> {
          if (message instanceof TestModelProvider.UserMessage userMessage
              && userMessage.isTextOnly()
              && userMessage.text().contains("look into")) {
            captured.set(userMessage.text());
          }
          return new AiResponse(completeTask("done"));
        });

    var taskId =
        componentClient
            .forAutonomousAgent(SanitizerAutonomousTestAgent.class, newSessionId())
            .runSingleTask(TestTasks.STRING_TASK.instructions("look into rolesecret"));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.STRING_TASK).result())
                    .isPresent());

    assertThat(captured.get()).contains("look into **********");
  }

  @Test
  public void shouldMaskEachApplicationPointWithItsOwnEntries() {
    var capturedUserMessage = new AtomicReference<String>();
    var capturedToolResult = new AtomicReference<String>();

    scopedAgentModel
        .whenMessage(message -> message.contains("notes for acme"))
        .reply(
            message -> {
              capturedUserMessage.set(message.content());
              return new AiResponse(
                  new ToolInvocationRequest(
                      "SanitizerTestAgent_getNotes", "{ \"customer\" : \"acme\" }"));
            });

    scopedAgentModel
        .whenToolResult(result -> result.name().equals("SanitizerTestAgent_getNotes"))
        .thenReply(
            result -> {
              capturedToolResult.set(result.content());
              return new AiResponse("done");
            });

    askScopedAgent("notes for acme, mind modelsecret and toolsecret");

    // model-call-only masks the question, tool-result-only leaves it alone
    assertThat(capturedUserMessage.get()).contains("mind *********** and toolsecret");
    // tool-result-only masks what the tool returned, model-call-only leaves it alone
    assertThat(capturedToolResult.get()).contains("********** and modelsecret");
  }

  @Test
  public void shouldInvokeAnImplementationForTheAgentItIsBoundTo() {
    var scopedCapture = captureUserMessage(scopedAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);

    askScopedAgent("mask recordedsecret for the bound agent");
    askOtherAgent("mask recordedsecret for the other agent");

    assertThat(scopedCapture.get()).contains("mask [recorded] for the bound agent");
    assertThat(otherCapture.get()).contains("mask recordedsecret for the other agent");

    assertThat(RecordingSanitizer.seen()).anyMatch(text -> text.contains("for the bound agent"));
    assertThat(RecordingSanitizer.seen()).noneMatch(text -> text.contains("for the other agent"));
  }

  @Test
  public void shouldNotMaskWithADisabledSanitizer() {
    var captured = captureUserMessage(scopedAgentModel);

    askScopedAgent("tell me about disabledsecret");

    assertThat(captured.get()).contains("tell me about disabledsecret");
    assertThatThrownBy(() -> getSanitizerClient().sanitize("switched-off", "disabledsecret"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No sanitizer configured with name [switched-off]");
  }

  @Test
  public void shouldMaskWithAPredefinedGroupForTheAgentTheEntryNames() {
    var scopedCapture = captureUserMessage(scopedAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);

    askScopedAgent("mail me at someone@example.com");
    askOtherAgent("mail me at someone@example.com");

    // the group is also selected by an entry that masks log messages, and that one masks no agent
    assertThat(scopedCapture.get()).contains("mail me at " + "*".repeat(19));
    assertThat(otherCapture.get()).contains("mail me at someone@example.com");
  }

  @Test
  public void shouldNotMaskForAnyAgentWithASanitizerForTheClientOnly() {
    var scopedCapture = captureUserMessage(scopedAgentModel);
    var otherCapture = captureUserMessage(otherAgentModel);

    askScopedAgent("tell me about clientsecret");
    askOtherAgent("tell me about clientsecret");

    assertThat(scopedCapture.get()).contains("tell me about clientsecret");
    assertThat(otherCapture.get()).contains("tell me about clientsecret");
  }

  @Test
  public void shouldMaskByName() {
    assertThat(getSanitizerClient().sanitize("agent-scoped", "a scopedsecret here"))
        .isEqualTo("a ************ here");
    assertThat(getSanitizerClient().sanitize("recording", "a recordedsecret here"))
        .isEqualTo("a [recorded] here");
    assertThat(getSanitizerClient().sanitize("by-name-only", "a clientsecret here"))
        .isEqualTo("a ************ here");
    assertThat(getSanitizerClient().sanitize("email-in-logs", "mail someone@example.com"))
        .isEqualTo("mail " + "*".repeat(19));

    var async =
        getSanitizerClient()
            .sanitizeAsync("recording", "another recordedsecret")
            .toCompletableFuture();
    assertThat(async.join()).isEqualTo("another [recorded]");
  }

  @Test
  public void shouldMaskByNameFromAComponent() {
    var response =
        httpClient
            .GET("/sanitize/recording/a%20recordedsecret%20here")
            .responseBodyAs(String.class)
            .invoke();

    assertThat(response.body()).isEqualTo("a [recorded] here");
  }

  @Test
  public void shouldFailByNameForAnUnconfiguredSanitizer() {
    assertThatThrownBy(() -> getSanitizerClient().sanitize("no-such-sanitizer", "text"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No sanitizer configured with name [no-such-sanitizer]")
        .hasMessageContaining("agent-scoped");

    var failed =
        getSanitizerClient().sanitizeAsync("no-such-sanitizer", "text").toCompletableFuture();
    assertThatThrownBy(() -> failed.get(5, TimeUnit.SECONDS))
        .isInstanceOf(ExecutionException.class)
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void sanitizerConstructorCanResolveADependencyProviderSuppliedDependency() {
    // "dependency-provided" (see test application.conf) takes a TestGrpcServiceClient constructor
    // param resolvable only via Bootstrap.createDependencyProvider(), not via any platform-managed
    // inject -- proves sanitizer construction runs after Bootstrap.createDependencyProvider().
    assertThat(getSanitizerClient().sanitize("dependency-provided", "anything"))
        .isEqualTo("resolved");
  }

  @Test
  @SuppressWarnings("removal")
  public void shouldKeepApplyingEveryDeclarativeEntryThroughTheDeprecatedHandle() {
    // The service wide handle has no agent, so it applies an entry that is bound to one.
    var masked = getSanitizer().sanitize("a scopedsecret and a rolesecret");

    assertThat(masked).isEqualTo("a ************ and a **********");
  }
}
