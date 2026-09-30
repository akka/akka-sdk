/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent;

import static akka.javasdk.testkit.TestModelProvider.AutonomousAgentTools.completeTask;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.SessionMemoryEntity;
import akka.javasdk.agent.SessionMessage;
import akka.javasdk.agent.SessionMessage.AiMessage;
import akka.javasdk.agent.SessionMessage.ToolCallResponse;
import akka.javasdk.agent.SessionMessage.UserMessage;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.Junit5LogCapturing;
import akkajavasdk.components.agent.autonomous.SanitizerAutonomousTestAgent;
import akkajavasdk.components.agent.autonomous.TestTasks;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Instant;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Covers which agent and which application point each configured sanitizer masks at, and what
 * session memory stores, for the sanitizers in the test application.conf.
 */
@ExtendWith(Junit5LogCapturing.class)
public class SanitizerIntegrationTest extends TestKitSupport {

  /**
   * Replies with a {@link TestModelProvider}, and records the messages of each request, history
   * included, which the {@link TestModelProvider} does not show.
   */
  private static final class RequestRecordingModelProvider implements ModelProvider.Custom {

    private final TestModelProvider replies;
    private final Queue<List<ChatMessage>> requests = new ConcurrentLinkedQueue<>();

    RequestRecordingModelProvider(TestModelProvider replies) {
      this.replies = replies;
    }

    List<ChatMessage> lastRequest() {
      return List.copyOf(requests).getLast();
    }

    void reset() {
      requests.clear();
    }

    @Override
    public String modelName() {
      return replies.modelName();
    }

    @Override
    public Object createChatModel() {
      var model = (ChatModel) replies.createChatModel();
      return new ChatModel() {
        @Override
        public ChatResponse doChat(ChatRequest request) {
          requests.add(request.messages());
          return model.doChat(request);
        }
      };
    }

    @Override
    public Object createStreamingChatModel() {
      var model = (StreamingChatModel) replies.createStreamingChatModel();
      return new StreamingChatModel() {
        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
          requests.add(request.messages());
          model.doChat(request, handler);
        }
      };
    }
  }

  private final TestModelProvider scopedAgentModel = new TestModelProvider();
  private final RequestRecordingModelProvider scopedAgentRequests =
      new RequestRecordingModelProvider(scopedAgentModel);
  private final TestModelProvider otherAgentModel = new TestModelProvider();
  private final TestModelProvider roleAgentModel = new TestModelProvider();
  private final TestModelProvider autonomousAgentModel = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT
        .withModelProvider(SanitizerTestAgent.class, scopedAgentRequests)
        .withModelProvider(SanitizerOtherTestAgent.class, otherAgentModel)
        .withModelProvider(SanitizerRoleTestAgent.class, roleAgentModel)
        .withModelProvider(SanitizerAutonomousTestAgent.class, autonomousAgentModel);
  }

  @AfterEach
  public void afterEach() {
    scopedAgentModel.reset();
    scopedAgentRequests.reset();
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
    askScopedAgent(newSessionId(), question);
  }

  private void askScopedAgent(String sessionId, String question) {
    componentClient
        .forAgent()
        .inSession(sessionId)
        .method(SanitizerTestAgent::query)
        .invoke(question);
  }

  private List<SessionMessage> storedHistory(String sessionId) {
    return componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::getHistory)
        .invoke(new SessionMemoryEntity.GetHistoryCmd())
        .messages();
  }

  private static <T extends ChatMessage> List<T> messagesOf(
      List<ChatMessage> request, Class<T> type) {
    return request.stream().filter(type::isInstance).map(type::cast).toList();
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

    runAutonomousTask(newSessionId(), "look into rolesecret");

    assertThat(captured.get()).contains("look into **********");
  }

  private void runAutonomousTask(String agentId, String instructions) {
    var taskId =
        componentClient
            .forAutonomousAgent(SanitizerAutonomousTestAgent.class, agentId)
            .runSingleTask(TestTasks.STRING_TASK.instructions(instructions));

    Awaitility.await()
        .ignoreExceptions()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(componentClient.forTask(taskId).get(TestTasks.STRING_TASK).result())
                    .isPresent());
  }

  @Test
  public void shouldStoreTheHistoryOfAnAutonomousAgentMaskedAndSanitized() {
    autonomousAgentModel.fixedResponse(new AiResponse(completeTask("done")));
    var agentId = newSessionId();

    runAutonomousTask(agentId, "look into rolesecret");

    Awaitility.await()
        .ignoreExceptions()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var history = storedHistory(agentId);
              assertThat(history)
                  .filteredOn(UserMessage.class::isInstance)
                  .map(message -> ((UserMessage) message).text())
                  .anyMatch(text -> text.contains("look into **********"))
                  .noneMatch(text -> text.contains("rolesecret"));
              assertThat(history).allMatch(SanitizerIntegrationTest::isSanitized);
            });
  }

  private static boolean isSanitized(SessionMessage message) {
    return switch (message) {
      case UserMessage m -> m.sanitized();
      case SessionMessage.MultimodalUserMessage m -> m.sanitized();
      case AiMessage m -> m.sanitized();
      case ToolCallResponse m -> m.sanitized();
      case SessionMessage.MultimodalToolCallResponse m -> m.sanitized();
    };
  }

  @Test
  public void shouldMaskAUserMessageWithModelCallEntriesAndAToolResultWithBoth() {
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
    // the model is sent the tool result too, so both entries mask it
    assertThat(capturedToolResult.get()).contains("acme: ********** and *********** and");
  }

  @Test
  public void shouldStoreTheHistoryAsTheModelWasSentItAndSendItAsStored() {
    var sessionId = newSessionId();
    var firstUserMessage = new AtomicReference<String>();
    var firstToolResult = new AtomicReference<String>();

    scopedAgentModel
        .whenMessage(message -> message.contains("notes for acme"))
        .reply(
            message -> {
              firstUserMessage.set(message.content());
              return new AiResponse(
                  new ToolInvocationRequest(
                      "SanitizerTestAgent_getNotes", "{ \"customer\" : \"acme\" }"));
            });
    scopedAgentModel
        .whenToolResult(result -> result.name().equals("SanitizerTestAgent_getNotes"))
        .thenReply(
            result -> {
              firstToolResult.set(result.content());
              return new AiResponse("the notes mention modelsecret");
            });
    scopedAgentModel.whenMessage(message -> message.contains("anything else")).reply("no");

    askScopedAgent(sessionId, "notes for acme, mind modelsecret");
    assertThat(firstUserMessage.get()).contains("mind ***********");
    assertThat(firstToolResult.get()).contains("acme: **********");

    RecordingSanitizer.reset();
    askScopedAgent(sessionId, "anything else?");

    // the runtime sends the history as stored, and masks only the new user message
    var secondTurn = scopedAgentRequests.lastRequest();
    assertThat(messagesOf(secondTurn, dev.langchain4j.data.message.UserMessage.class))
        .map(dev.langchain4j.data.message.UserMessage::singleText)
        .containsExactly(firstUserMessage.get(), "anything else?");
    assertThat(messagesOf(secondTurn, ToolExecutionResultMessage.class))
        .map(ToolExecutionResultMessage::text)
        .containsExactly(firstToolResult.get());
    // model output is sent as the model produced it
    assertThat(messagesOf(secondTurn, dev.langchain4j.data.message.AiMessage.class))
        .map(dev.langchain4j.data.message.AiMessage::text)
        .contains("the notes mention modelsecret");
    assertThat(RecordingSanitizer.seen()).containsExactly("anything else?");

    // the history holds the text the model was sent on the first turn
    var history = storedHistory(sessionId);
    assertThat(history).hasSize(6);
    var userMessage = (UserMessage) history.get(0);
    assertThat(userMessage.text()).isEqualTo(firstUserMessage.get());
    assertThat(userMessage.sanitized()).isTrue();
    var toolResult = (ToolCallResponse) history.get(2);
    assertThat(toolResult.text()).isEqualTo(firstToolResult.get());
    assertThat(toolResult.sanitized()).isTrue();
    var aiMessage = (AiMessage) history.get(3);
    assertThat(aiMessage.text()).isEqualTo("the notes mention modelsecret");
    assertThat(aiMessage.sanitized()).isTrue();
  }

  @Test
  public void shouldMaskAStoredMessageThatIsNotSanitized() {
    var sessionId = newSessionId();
    var now = Instant.now();
    componentClient
        .forEventSourcedEntity(sessionId)
        .method(SessionMemoryEntity::addInteraction)
        .invoke(
            new SessionMemoryEntity.AddInteractionCmd(
                new UserMessage(now, "earlier I said scopedsecret", "sanitizer-test-agent"),
                new AiMessage(now, "noted", "sanitizer-test-agent")));
    captureUserMessage(scopedAgentModel);

    askScopedAgent(sessionId, "what did I say?");

    assertThat(
            messagesOf(
                scopedAgentRequests.lastRequest(), dev.langchain4j.data.message.UserMessage.class))
        .map(dev.langchain4j.data.message.UserMessage::singleText)
        .containsExactly("earlier I said ************", "what did I say?");
    assertThat(RecordingSanitizer.seen()).anyMatch(text -> text.contains("earlier I said"));
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
