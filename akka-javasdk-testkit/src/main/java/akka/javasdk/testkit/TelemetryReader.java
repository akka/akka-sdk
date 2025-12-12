/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static java.util.Optional.ofNullable;

import akka.annotation.ApiMayChange;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import kalix.runtime.telemetry.tracing.TracingSetup;

@ApiMayChange
public class TelemetryReader {

  private final TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter;

  public TelemetryReader(TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter) {
    this.inMemorySpanExporter = inMemorySpanExporter;
  }

  @ApiMayChange
  public List<String> getWorkflowSteps(Class<? extends Workflow<?>> workflow, String workflowId) {
    String componentId = getComponentId(workflow);
    List<SpanData> spanDatas =
        spansFor(componentId, AttributeKey.stringKey("akka.workflow.id"), workflowId);

    return workflowStepsFrom(spanDatas);
  }

  @ApiMayChange
  public List<String> getAgents(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    AttributeKey<String> agentToolAttributeKey = AttributeKey.stringKey("gen_ai.agent.id");

    return collectAttributeValues(spanDatas, agentToolAttributeKey);
  }

  @ApiMayChange
  public List<String> getAgentTools(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    AttributeKey<String> agentToolAttributeKey = AttributeKey.stringKey("gen_ai.tool.name");

    return collectAttributeValues(spanDatas, agentToolAttributeKey);
  }

  @ApiMayChange
  public List<String> getWorkflowSteps(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    return workflowStepsFrom(spanDatas);
  }

  private List<String> collectAttributeValues(
      List<SpanData> spanDatas, AttributeKey<String> agentToolAttributeKey) {
    List<SpanData> stepSpanDatas = collectByAttribute(spanDatas, agentToolAttributeKey);

    return stepSpanDatas.stream()
        .sorted(Comparator.comparing(SpanData::getStartEpochNanos))
        .map(spanData -> spanData.getAttributes().get(agentToolAttributeKey))
        .toList();
  }

  private List<String> workflowStepsFrom(List<SpanData> spanDatas) {
    AttributeKey<String> workflowStepNameAttribute =
        AttributeKey.stringKey("akka.workflow.step.name");

    return collectAttributeValues(spanDatas, workflowStepNameAttribute);
  }

  private String getComponentId(Class<?> componentClass) {
    Component componentAnnotation = componentClass.getAnnotation(Component.class);
    if (componentAnnotation != null) {
      return componentAnnotation.id();
    }

    // Fallback to the old ComponentId annotation for backward compatibility
    return componentClass.getAnnotation(ComponentId.class).value();
  }

  private List<SpanData> collectByAttribute(
      List<SpanData> spanDatas, AttributeKey<String> attributeKey) {
    return spanDatas.stream()
        .filter(spanData -> spanData.getAttributes().get(attributeKey) != null)
        .toList();
  }

  private List<SpanData> spansFor(
      String componentId, AttributeKey<String> attributeKey, String attributeValue) {

    return inMemorySpanExporter.getFinishedSpanItems().stream()
        .filter(
            spanData -> {
              return ofNullable(
                          spanData.getAttributes().get(AttributeKey.stringKey("akka.component.id")))
                      .map(value -> value.equals(componentId))
                      .orElse(false)
                  && ofNullable(spanData.getAttributes().get(attributeKey))
                      .map(value -> value.equals(attributeValue))
                      .orElse(false);
            })
        .toList();
  }

  private List<SpanData> spansFor(String debugId) {
    Optional<SpanData> mainSpan =
        inMemorySpanExporter.getFinishedSpanItems().stream()
            .filter(
                spanData -> {
                  return ofNullable(
                          spanData.getAttributes().get(AttributeKey.stringKey("akka.debug.id")))
                      .map(value -> value.equals(debugId))
                      .orElse(false);
                })
            .findFirst();

    return mainSpan
        .map(
            spanData ->
                inMemorySpanExporter.getFinishedSpanItems().stream()
                    .filter(span -> span.getTraceId().equals(spanData.getTraceId()))
                    .toList())
        .orElse(List.of());
  }
}
