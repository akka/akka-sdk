/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import akka.annotation.ApiMayChange;
import akka.javasdk.JsonSupport;
import akka.javasdk.annotations.Component;
import akka.javasdk.workflow.Workflow;
import akka.runtime.sdk.spi.tracing.InMemorySpanExporter;
import com.fasterxml.jackson.core.type.TypeReference;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the spans the runtime exports to the in-memory span exporter of a running TestKit. Use it
 * to verify workflow steps, agent tool calls and model calls in integration tests.
 *
 * <p>Spans are exported after the traced operation completes. Except for {@link
 * #getAgentTrace(String)}, the methods do not wait, so poll them from an assertion that retries.
 */
@ApiMayChange
public class TelemetryReader {

  // Span attributes the runtime records, see the tracing conventions in the runtime.
  private static final AttributeKey<String> SPAN_KIND = AttributeKey.stringKey("akka.span.kind");
  private static final AttributeKey<String> DEBUG_ID = AttributeKey.stringKey("akka.debug.id");
  private static final AttributeKey<String> COMPONENT_ID =
      AttributeKey.stringKey("akka.component.id");
  private static final AttributeKey<String> IMPLEMENTATION =
      AttributeKey.stringKey("akka.component.implementation.name");
  private static final AttributeKey<String> WORKFLOW_ID =
      AttributeKey.stringKey("akka.workflow.id");
  private static final AttributeKey<String> WORKFLOW_STEP_NAME =
      AttributeKey.stringKey("akka.workflow.step.name");
  private static final AttributeKey<String> AGENT_ID = AttributeKey.stringKey("gen_ai.agent.id");
  private static final AttributeKey<String> SESSION_ID =
      AttributeKey.stringKey("gen_ai.conversation.id");
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

  // Values of SPAN_KIND
  private static final String AGENT_COMMAND_SPAN = "agent.command";
  private static final String TOOL_CALL_SPAN = "agent.tool.call";
  private static final String MODEL_CALL_SPAN = "agent.model.call";
  private static final String GUARDRAIL_SPAN = "agent.guardrail";

  private static final TypeReference<Map<String, Object>> ARGUMENTS = new TypeReference<>() {};
  private static final Duration DEFAULT_TRACE_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration POLL = Duration.ofMillis(50);

  private final InMemorySpanExporter inMemorySpanExporter;

  public TelemetryReader(InMemorySpanExporter inMemorySpanExporter) {
    this.inMemorySpanExporter =
        Objects.requireNonNull(inMemorySpanExporter, "inMemorySpanExporter required");
  }

  /**
   * The step names of one workflow instance, in execution order.
   *
   * @param workflow the workflow class, annotated with {@link Component}
   * @param workflowId the workflow instance id
   * @return the step names, empty when no step has been traced yet
   */
  @ApiMayChange
  public List<String> getWorkflowSteps(Class<? extends Workflow<?>> workflow, String workflowId) {
    var componentId = getComponentId(workflow);
    var steps =
        spans()
            .filter(span -> hasAttribute(span, COMPONENT_ID, componentId))
            .filter(span -> hasAttribute(span, WORKFLOW_ID, workflowId));
    return attributeValues(steps, WORKFLOW_STEP_NAME);
  }

  /**
   * The step names traced in the operation that carries the given debug id, in execution order.
   *
   * @param debugId the debug id of the traced operation
   * @return the step names, empty when no step has been traced yet
   */
  @ApiMayChange
  public List<String> getWorkflowSteps(String debugId) {
    return attributeValues(spansInTraceOf(debugId), WORKFLOW_STEP_NAME);
  }

  /**
   * The ids of the agents invoked in the operation that carries the given debug id, in invocation
   * order.
   *
   * @param debugId the debug id of the traced operation
   * @return the agent ids, empty when no agent has been traced yet
   */
  @ApiMayChange
  public List<String> getAgents(String debugId) {
    return attributeValues(spansInTraceOf(debugId), AGENT_ID);
  }

  /**
   * The names of the tools called in the operation that carries the given debug id, in call order.
   * Names are as the model sees them, with the agent class prefix.
   *
   * @param debugId the debug id of the traced operation
   * @return the tool names, empty when no tool call has been traced yet
   */
  @ApiMayChange
  public List<String> getAgentTools(String debugId) {
    return attributeValues(spansInTraceOf(debugId), TOOL_NAME);
  }

  /**
   * Everything the runtime traced under the agent commands of one session: tool calls, model calls
   * and guardrail evaluations, each in call order. Waits up to 5 seconds for the first command span
   * of the session.
   *
   * <p>Tool names are reported without the agent class prefix, see {@link ToolCall}.
   *
   * @param sessionId the agent session id
   * @throws IllegalStateException when no agent command for the session is traced within the
   *     timeout
   */
  @ApiMayChange
  public AgentTrace getAgentTrace(String sessionId) {
    return getAgentTrace(sessionId, DEFAULT_TRACE_TIMEOUT);
  }

  /**
   * The same as {@link #getAgentTrace(String)} with another timeout.
   *
   * @param sessionId the agent session id
   * @param timeout how long to wait for the first command span of the session
   * @throws IllegalStateException when no agent command for the session is traced within the
   *     timeout
   */
  @ApiMayChange
  public AgentTrace getAgentTrace(String sessionId, Duration timeout) {
    awaitAgentCommand(sessionId, timeout);
    var commands = agentCommandsIn(sessionId).sorted(byStart()).toList();
    var commandIds = commands.stream().map(SpanData::getSpanId).collect(Collectors.toSet());
    var children =
        spans()
            .filter(span -> commandIds.contains(span.getParentSpanId()))
            .sorted(byStart())
            .toList();

    var toolCalls = ofKind(children, TOOL_CALL_SPAN).map(TelemetryReader::toToolCall).toList();
    var modelCalls = ofKind(children, MODEL_CALL_SPAN).map(TelemetryReader::toModelCall).toList();
    var guardrails = ofKind(children, GUARDRAIL_SPAN).map(TelemetryReader::toGuardrail).toList();
    var started = commands.getFirst().getStartEpochNanos();
    var ended = commands.stream().mapToLong(SpanData::getEndEpochNanos).max().orElse(started);
    var finalText = modelCalls.isEmpty() ? "" : finalText(modelCalls.getLast().outputMessages());
    return new AgentTrace(
        toolCalls, modelCalls, guardrails, Duration.ofNanos(ended - started), finalText);
  }

  private Stream<SpanData> spans() {
    return inMemorySpanExporter.getFinishedSpanItems().stream();
  }

  /** Every span of the trace whose span carries the debug id. Empty when none does. */
  private Stream<SpanData> spansInTraceOf(String debugId) {
    return spans()
        .filter(span -> hasAttribute(span, DEBUG_ID, debugId))
        .findFirst()
        .map(SpanData::getTraceId)
        .map(traceId -> spans().filter(span -> traceId.equals(span.getTraceId())))
        .orElseGet(Stream::empty);
  }

  private Stream<SpanData> agentCommandsIn(String sessionId) {
    return spans()
        .filter(span -> hasAttribute(span, SPAN_KIND, AGENT_COMMAND_SPAN))
        .filter(span -> hasAttribute(span, SESSION_ID, sessionId));
  }

  /** The values of the attribute on the spans that carry it, in span start order. */
  private static List<String> attributeValues(Stream<SpanData> spans, AttributeKey<String> key) {
    return spans
        .sorted(byStart())
        .map(span -> span.getAttributes().get(key))
        .filter(Objects::nonNull)
        .toList();
  }

  private static boolean hasAttribute(SpanData span, AttributeKey<String> key, String value) {
    return value.equals(span.getAttributes().get(key));
  }

  private static Comparator<SpanData> byStart() {
    return Comparator.comparingLong(SpanData::getStartEpochNanos);
  }

  private static Stream<SpanData> ofKind(List<SpanData> spans, String kind) {
    return spans.stream().filter(span -> hasAttribute(span, SPAN_KIND, kind));
  }

  private static String getComponentId(Class<?> componentClass) {
    var component = componentClass.getAnnotation(Component.class);
    if (component == null) {
      throw new IllegalArgumentException(
          "Component [" + componentClass + "] is missing @Component annotation");
    }
    return component.id();
  }

  private void awaitAgentCommand(String sessionId, Duration timeout) {
    var deadline = System.nanoTime() + timeout.toNanos();
    while (agentCommandsIn(sessionId).findAny().isEmpty()) {
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

  // The rendered output messages are a JSON array of messages with parts. Returns the text parts
  // of the last message.
  private static String finalText(String outputMessages) {
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

  // An agent-local tool is registered as <AgentClass>_<method>. The span names the implementing
  // class, possibly qualified. Without it, a leading capitalised segment before an underscore is
  // taken as the class. A method name or an MCP tool name does not start that way.
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
