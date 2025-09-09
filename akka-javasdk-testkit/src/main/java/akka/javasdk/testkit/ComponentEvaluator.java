/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;

public class ComponentEvaluator {

  private final InMemorySpanExporter inMemorySpanExporter;

  public ComponentEvaluator(InMemorySpanExporter inMemorySpanExporter) {
    this.inMemorySpanExporter = inMemorySpanExporter;
  }

  public List<String> getWorkflowSteps(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    AttributeKey<String> workflowStepNameAttribute =
        AttributeKey.stringKey("akka.workflow.step.name");

    List<SpanData> stepSpanDatas = collectByAttribute(spanDatas, workflowStepNameAttribute);

    return stepSpanDatas.stream()
        .sorted(Comparator.comparing(SpanData::getStartEpochNanos))
        .map(spanData -> spanData.getAttributes().get(workflowStepNameAttribute))
        .toList();
  }

  private List<SpanData> collectByAttribute(
      List<SpanData> spanDatas, AttributeKey<String> attributeKey) {
    return spanDatas.stream()
        .filter(spanData -> spanData.getAttributes().get(attributeKey) != null)
        .toList();
  }

  private List<SpanData> spansFor(String debugId) {
    Optional<SpanData> mainSpan =
        inMemorySpanExporter.getFinishedSpanItems().stream()
            .filter(
                spanData -> {
                  return
                      ofNullable(spanData.getAttributes().get(AttributeKey.stringKey("akka.debug.id")))
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
