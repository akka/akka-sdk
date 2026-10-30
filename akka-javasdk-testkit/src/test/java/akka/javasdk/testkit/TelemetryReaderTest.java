/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.GuardrailResult.Decision;
import akka.runtime.sdk.spi.tracing.InMemorySpanExporter;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class TelemetryReaderTest {

  private static final String SESSION = "session-1";

  @Test
  void aFunctionToolLosesItsClassPrefix() {
    assertThat(
            TelemetryReader.toolName("SupportAgent_getCustomer", "com.example.SupportAgent", false))
        .isEqualTo("getCustomer");
    assertThat(TelemetryReader.toolName("WeatherTools_forecast", "com.example.SupportAgent", false))
        .isEqualTo("forecast");
    assertThat(TelemetryReader.toolName("SupportAgent_getCustomer", null, false))
        .isEqualTo("getCustomer");
    assertThat(TelemetryReader.toolName("get_customer", null, false)).isEqualTo("get_customer");
  }

  @Test
  void anMcpToolKeepsTheNameItsServerGaveIt() {
    assertThat(TelemetryReader.toolName("Github_createIssue", "com.example.SupportAgent", true))
        .isEqualTo("Github_createIssue");
    assertThat(TelemetryReader.toolName("Github_createIssue", null, true))
        .isEqualTo("Github_createIssue");
  }

  /** Spans kept in memory, as the TestKit exports them. */
  private static final class Spans implements InMemorySpanExporter {
    private final List<SpanData> finished = new CopyOnWriteArrayList<>();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      finished.addAll(spans);
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public List<SpanData> getFinishedSpanItems() {
      return List.copyOf(finished);
    }

    @Override
    public void reset() {
      finished.clear();
    }
  }

  /**
   * Traces one agent command with one guardrail span under it and reads the guardrail back.
   *
   * @param decision the decision attribute of the guardrail span; null leaves it out
   */
  private static GuardrailResult readGuardrail(
      String decision, StatusCode status, String description, StatusCode commandStatus) {
    var spans = new Spans();
    try (var tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build()) {
      var tracer = tracerProvider.get("test");

      var command =
          tracer
              .spanBuilder("Agent command")
              .setAttribute("akka.span.kind", "agent.command")
              .setAttribute("gen_ai.conversation.id", SESSION)
              .startSpan();

      var guardrail =
          tracer
              .spanBuilder("Guardrail safety evaluation: jailbreak")
              .setParent(Context.root().with(command))
              .setAttribute("akka.span.kind", "agent.guardrail")
              .setAttribute("akka.agent.guardrail.name", "jailbreak")
              .setAttribute("akka.agent.guardrail.category", "safety")
              .startSpan();
      if (decision != null) guardrail.setAttribute("akka.agent.guardrail.decision", decision);
      guardrail.setStatus(status, description);
      guardrail.end();

      command.setStatus(commandStatus);
      command.end();
    }
    return new TelemetryReader(spans).getAgentTrace(SESSION).guardrails().getFirst();
  }

  /**
   * Traces one agent command with one tool call and a denying guardrail span under the tool call.
   *
   * @param enforced whether the denial stopped the tool call
   */
  private static AgentTrace traceToolCallDenial(boolean enforced) {
    var spans = new Spans();
    try (var tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build()) {
      var tracer = tracerProvider.get("test");

      var command =
          tracer
              .spanBuilder("Agent command")
              .setAttribute("akka.span.kind", "agent.command")
              .setAttribute("gen_ai.conversation.id", SESSION)
              .startSpan();

      var toolCall =
          tracer
              .spanBuilder("Tool call: deleteAccount")
              .setParent(Context.root().with(command))
              .setAttribute("akka.span.kind", "agent.tool.call")
              .setAttribute("gen_ai.tool.name", "deleteAccount")
              .startSpan();

      var guardrail =
          tracer
              .spanBuilder("Guardrail tool policy evaluation: deny delete")
              .setParent(Context.root().with(toolCall))
              .setAttribute("akka.span.kind", "agent.guardrail")
              .setAttribute("akka.agent.guardrail.name", "deny delete")
              .setAttribute("akka.agent.guardrail.category", "tool_policy")
              .setAttribute("akka.agent.guardrail.decision", "deny")
              .startSpan();
      guardrail.setStatus(StatusCode.ERROR, "deletion not allowed");
      guardrail.end();

      if (enforced) {
        toolCall.setStatus(StatusCode.ERROR, "deletion not allowed");
      } else {
        toolCall.setAttribute("gen_ai.tool.call.result", "deleted");
      }
      toolCall.end();
      command.end();
    }
    return new TelemetryReader(spans).getAgentTrace(SESSION);
  }

  @Test
  void aGuardrailSpanUnderAToolCallIsRead() {
    var trace = traceToolCallDenial(true);

    assertThat(trace.guardrails())
        .containsExactly(
            new GuardrailResult(
                "deny delete", "tool_policy", Decision.DENY, "deletion not allowed"));
    assertThat(trace.toolCalls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.name()).isEqualTo("deleteAccount");
              assertThat(call.deniedBy()).contains("deny delete");
            });
  }

  @Test
  void aReportOnlyDenialUnderAToolCallLeavesTheCallNotDenied() {
    var trace = traceToolCallDenial(false);

    assertThat(trace.guardrails())
        .singleElement()
        .satisfies(guardrail -> assertThat(guardrail.decision()).isEqualTo(Decision.DENY));
    assertThat(trace.toolCalls())
        .singleElement()
        .satisfies(
            call -> {
              assertThat(call.result()).contains("deleted");
              assertThat(call.deniedBy()).isEmpty();
            });
  }

  @Test
  void aDenyingGuardrailSpanIsReadAsDeny() {
    var guardrail = readGuardrail("deny", StatusCode.ERROR, "prompt injection", StatusCode.ERROR);

    assertThat(guardrail)
        .isEqualTo(new GuardrailResult("jailbreak", "safety", Decision.DENY, "prompt injection"));
    assertThat(guardrail.passed()).isFalse();
  }

  @Test
  void anErroringGuardrailSpanIsReadAsError() {
    var guardrail = readGuardrail("error", StatusCode.ERROR, "model timeout", StatusCode.ERROR);

    assertThat(guardrail.decision()).isEqualTo(Decision.ERROR);
    assertThat(guardrail.passed()).isFalse();
    assertThat(guardrail.explanation()).isEqualTo("model timeout");
  }

  @Test
  void aGuardrailSpanWithoutDecisionAndWithErrorStatusIsReadAsError() {
    var guardrail = readGuardrail(null, StatusCode.ERROR, "prompt injection", StatusCode.ERROR);

    assertThat(guardrail.decision()).isEqualTo(Decision.ERROR);
    assertThat(guardrail.passed()).isFalse();
    assertThat(guardrail.explanation()).isEqualTo("prompt injection");
  }

  @Test
  void aGuardrailSpanWithoutDecisionAndOkStatusIsReadAsAllow() {
    var guardrail = readGuardrail(null, StatusCode.OK, "", StatusCode.OK);

    assertThat(guardrail.decision()).isEqualTo(Decision.ALLOW);
    assertThat(guardrail.passed()).isTrue();
    assertThat(guardrail.explanation()).isEmpty();
  }

  @Test
  void aReportOnlyDenialIsReadAsDeny() {
    var guardrail = readGuardrail("deny", StatusCode.ERROR, "prompt injection", StatusCode.OK);

    assertThat(guardrail.decision()).isEqualTo(Decision.DENY);
    assertThat(guardrail.passed()).isFalse();
    assertThat(guardrail.explanation()).isEqualTo("prompt injection");
  }
}
