/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import static java.util.Optional.ofNullable;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import kalix.runtime.telemetry.tracing.TracingSetup;

public class TelemetryReader {

  private final TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter;

  public TelemetryReader(TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter) {
    this.inMemorySpanExporter = inMemorySpanExporter;
  }

  public List<String> getWorkflowSteps(Class<? extends Workflow<?>> workflow, String workflowId) {
    String componentId = getComponentId(workflow);
    List<SpanData> spanDatas =
        spansFor(componentId, AttributeKey.stringKey("akka.workflow.id"), workflowId);

    return workflowStepsFrom(spanDatas);
  }

  public List<String> getWorkflowSteps(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    return workflowStepsFrom(spanDatas);
  }

  private List<String> workflowStepsFrom(List<SpanData> spanDatas) {
    AttributeKey<String> workflowStepNameAttribute =
        AttributeKey.stringKey("akka.workflow.step.name");

    List<SpanData> stepSpanDatas = collectByAttribute(spanDatas, workflowStepNameAttribute);

    return stepSpanDatas.stream()
        .sorted(Comparator.comparing(SpanData::getStartEpochNanos))
        .map(spanData -> spanData.getAttributes().get(workflowStepNameAttribute))
        .toList();
  }

  private String getComponentId(Class<?> componentClass) {
    return componentClass.getAnnotation(ComponentId.class).value();
  }

  private List<SpanData> collectByAttribute(
      List<SpanData> spanDatas, AttributeKey<String> attributeKey) {
    return spanDatas.stream()
        .filter(spanData -> spanData.getAttributes().get(attributeKey) != null)
        .toList();
  }

  private List<SpanData> spansFor(
      String componentId, AttributeKey<String> idAttributeKey, String id) {
    Optional<SpanData> mainSpan =
        inMemorySpanExporter.getFinishedSpanItems().stream()
            .filter(
                spanData -> {
                  return ofNullable(
                              spanData
                                  .getAttributes()
                                  .get(AttributeKey.stringKey("akka.component.id")))
                          .map(value -> value.equals(componentId))
                          .orElse(false)
                      && ofNullable(spanData.getAttributes().get(idAttributeKey))
                          .map(value -> value.equals(id))
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
