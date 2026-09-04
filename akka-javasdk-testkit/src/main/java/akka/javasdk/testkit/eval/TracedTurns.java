/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.JsonSupport;
import akka.runtime.sdk.spi.tracing.InMemorySpanExporter;
import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The evidence of a turn, read from the trace the runtime records for an agent call.
 *
 * <p>Under the agent's command span the runtime opens one span per tool call, with the tool's name,
 * arguments, result and error status; one per model call, with the messages both ways, the finish
 * reason and the token counts; and one per guardrail evaluation, with its verdict. Under the
 * TestKit every trace carries those payloads and every span stays in memory, so a target can call
 * the agent through the component client and then read back what happened, including the calls made
 * before a failure.
 *
 * <p>The agent's command span names the session it ran in, which is how this reader finds the
 * trace: the runner hands each case a fresh session id, so a session is one case's evidence.
 *
 * <p>{@link ExperimentRunner#forAgent} reads through this on every case. Any integration test can
 * as well:
 *
 * <pre>{@code
 * var traced = TracedTurns.from(testKit.getInMemorySpanExporter());
 * var turn = traced.forSession(sessionId);
 * turn.toolCalls();
 * turn.modelCalls();
 * }</pre>
 *
 * <p>Tool names are reported without the agent prefix the model sees, matching what {@link
 * ToolCall} documents.
 */
public final class TracedTurns {

  private static final AttributeKey<String> SESSION_ID =
      AttributeKey.stringKey("gen_ai.conversation.id");
  private static final AttributeKey<String> SPAN_KIND = AttributeKey.stringKey("akka.span.kind");
  private static final AttributeKey<String> IMPLEMENTATION =
      AttributeKey.stringKey("akka.component.implementation.name");
  private static final AttributeKey<String> TOOL_NAME = AttributeKey.stringKey("gen_ai.tool.name");
  private static final AttributeKey<String> TOOL_ARGUMENTS =
      AttributeKey.stringKey("gen_ai.tool.call.arguments");
  private static final AttributeKey<String> TOOL_RESULT =
      AttributeKey.stringKey("gen_ai.tool.call.result");
  private static final AttributeKey<String> MODEL = AttributeKey.stringKey("gen_ai.request.model");
  private static final AttributeKey<String> PROVIDER =
      AttributeKey.stringKey("gen_ai.provider.name");
  private static final AttributeKey<List<String>> FINISH_REASONS =
      AttributeKey.stringArrayKey("gen_ai.response.finish_reasons");
  private static final AttributeKey<Long> INPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.input_tokens");
  private static final AttributeKey<Long> OUTPUT_TOKENS =
      AttributeKey.longKey("gen_ai.usage.output_tokens");
  private static final AttributeKey<String> INPUT_MESSAGES =
      AttributeKey.stringKey("gen_ai.input.messages");
  private static final AttributeKey<String> OUTPUT_MESSAGES =
      AttributeKey.stringKey("gen_ai.output.messages");
  private static final AttributeKey<String> GUARDRAIL_NAME =
      AttributeKey.stringKey("akka.agent.guardrail.name");
  private static final AttributeKey<String> GUARDRAIL_CATEGORY =
      AttributeKey.stringKey("akka.agent.guardrail.category");
  private static final AttributeKey<String> GUARDRAIL_RESULT =
      AttributeKey.stringKey("akka.agent.guardrail.result");
  private static final String AGENT_COMMAND_SPAN = "agent.command";
  private static final String TOOL_CALL_SPAN = "agent.tool.call";
  private static final String MODEL_CALL_SPAN = "agent.model.call";
  private static final String GUARDRAIL_SPAN = "agent.guardrail";

  private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {};
  private static final Duration POLL = Duration.ofMillis(50);

  private final InMemorySpanExporter exporter;
  private final Duration timeout;

  private TracedTurns(InMemorySpanExporter exporter, Duration timeout) {
    this.exporter = exporter;
    this.timeout = timeout;
  }

  /** Over the TestKit's exporter, waiting a few seconds for the trace to be exported. */
  public static TracedTurns from(InMemorySpanExporter exporter) {
    if (exporter == null) throw new IllegalArgumentException("exporter required");
    return new TracedTurns(exporter, Duration.ofSeconds(5));
  }

  public TracedTurns waitingUpTo(Duration timeout) {
    return new TracedTurns(exporter, timeout);
  }

  /**
   * What the agent commands that ran in this session did, in order. Waits for the first command
   * span, which the runtime exports once the call has been answered, so every span under it is
   * there by then.
   *
   * @throws IllegalStateException when no agent command in that session shows up in time
   */
  public TracedTurn forSession(String sessionId) {
    awaitCommand(sessionId);
    var commands =
        exporter.getFinishedSpanItems().stream()
            .filter(span -> isCommandIn(span, sessionId))
            .sorted(Comparator.comparingLong(SpanData::getStartEpochNanos))
            .toList();
    var commandIds = commands.stream().map(SpanData::getSpanId).collect(Collectors.toSet());
    var children =
        exporter.getFinishedSpanItems().stream()
            .filter(span -> commandIds.contains(span.getParentSpanId()))
            .sorted(Comparator.comparingLong(SpanData::getStartEpochNanos))
            .toList();

    var toolCalls = ofKind(children, TOOL_CALL_SPAN).map(TracedTurns::toToolCall).toList();
    var modelCalls = ofKind(children, MODEL_CALL_SPAN).map(TracedTurns::toModelCall).toList();
    var guardrails = ofKind(children, GUARDRAIL_SPAN).map(TracedTurns::toGuardrail).toList();
    var started = commands.getFirst().getStartEpochNanos();
    var ended = commands.stream().mapToLong(SpanData::getEndEpochNanos).max().orElse(started);
    var finalText = modelCalls.isEmpty() ? "" : finalText(modelCalls.getLast().outputMessages());
    return new TracedTurn(
        toolCalls, modelCalls, guardrails, Duration.ofNanos(ended - started), finalText);
  }

  private static Stream<SpanData> ofKind(List<SpanData> spans, String kind) {
    return spans.stream().filter(span -> kind.equals(span.getAttributes().get(SPAN_KIND)));
  }

  private static boolean isCommandIn(SpanData span, String sessionId) {
    var attributes = span.getAttributes();
    return AGENT_COMMAND_SPAN.equals(attributes.get(SPAN_KIND))
        && sessionId.equals(attributes.get(SESSION_ID));
  }

  private void awaitCommand(String sessionId) {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (exporter.getFinishedSpanItems().stream()
        .noneMatch(span -> isCommandIn(span, sessionId))) {
      if (System.nanoTime() > deadline) {
        throw new IllegalStateException(
            "no agent command in session " + sessionId + " within " + timeout);
      }
      try {
        Thread.sleep(POLL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("interrupted while waiting for the trace", e);
      }
    }
  }

  private static ToolCall toToolCall(SpanData span) {
    var attributes = span.getAttributes();
    var name = unprefixed(attributes.get(TOOL_NAME), attributes.get(IMPLEMENTATION));
    var error =
        span.getStatus().getStatusCode() == StatusCode.ERROR
            ? Optional.of(span.getStatus().getDescription())
            : Optional.<String>empty();
    return new ToolCall(
        name,
        parseArguments(attributes.get(TOOL_ARGUMENTS)),
        Optional.ofNullable(attributes.get(TOOL_RESULT)),
        error);
  }

  private static ModelCall toModelCall(SpanData span) {
    var attributes = span.getAttributes();
    return new ModelCall(
        attributes.get(MODEL),
        attributes.get(PROVIDER),
        attributes.get(FINISH_REASONS),
        longOrZero(attributes.get(INPUT_TOKENS)),
        longOrZero(attributes.get(OUTPUT_TOKENS)),
        Duration.ofNanos(span.getEndEpochNanos() - span.getStartEpochNanos()),
        attributes.get(INPUT_MESSAGES),
        attributes.get(OUTPUT_MESSAGES));
  }

  private static long longOrZero(Long value) {
    return value == null ? 0 : value;
  }

  private static GuardrailResult toGuardrail(SpanData span) {
    var attributes = span.getAttributes();
    var blocked = span.getStatus().getStatusCode() == StatusCode.ERROR;
    return new GuardrailResult(
        attributes.get(GUARDRAIL_NAME),
        attributes.get(GUARDRAIL_CATEGORY),
        !blocked && !"fail".equals(attributes.get(GUARDRAIL_RESULT)),
        blocked ? span.getStatus().getDescription() : "");
  }

  /**
   * The text of the last message the model wrote, out of the messages the runtime rendered for the
   * span: a JSON array of messages, each with parts, of which the text parts count.
   */
  static String finalText(String outputMessages) {
    if (outputMessages == null || outputMessages.isBlank()) return "";
    try {
      var messages = JsonSupport.getObjectMapper().readTree(outputMessages);
      if (!messages.isArray() || messages.isEmpty()) return "";
      var text = new StringBuilder();
      for (var part : messages.get(messages.size() - 1).path("parts")) {
        if ("text".equals(part.path("type").asText())) text.append(part.path("content").asText());
      }
      return text.toString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * An agent-local tool is registered as {@code <AgentClass>_<method>}; drop the class. The span
   * names the implementing class, possibly qualified; without it, a leading capitalised segment
   * before an underscore is taken as the class, which a method name or an MCP tool name never
   * starts with.
   */
  private static String unprefixed(String toolName, String implementation) {
    if (toolName == null) return "?";
    if (implementation != null) {
      var simple = implementation.substring(implementation.lastIndexOf('.') + 1);
      if (toolName.startsWith(simple + "_")) return toolName.substring(simple.length() + 1);
    }
    var underscore = toolName.indexOf('_');
    if (underscore > 0 && Character.isUpperCase(toolName.charAt(0))) {
      return toolName.substring(underscore + 1);
    }
    return toolName;
  }

  private static Map<String, Object> parseArguments(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return JsonSupport.getObjectMapper().readValue(json, ARGUMENTS);
    } catch (Exception e) {
      return Map.of("_raw", json);
    }
  }
}
