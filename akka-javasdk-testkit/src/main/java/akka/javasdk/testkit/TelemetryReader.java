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

/**
 * A test utility for reading and inspecting telemetry data captured during test execution.
 *
 * <p>TelemetryReader provides access to OpenTelemetry spans collected by the in-memory span
 * exporter during integration tests. This allows you to verify workflow execution, agent
 * interactions, and other instrumented operations.
 *
 */
@ApiMayChange
public class TelemetryReader {

  private final TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter;

  public TelemetryReader(TracingSetup.AkkaInMemorySpanExporter inMemorySpanExporter) {
    this.inMemorySpanExporter = inMemorySpanExporter;
  }

  /**
   * Retrieves the sequence of workflow steps executed for a specific workflow instance.
   *
   * <p>Returns a list of step names in the order they were executed, based on telemetry data
   * collected during the workflow execution. This is useful for verifying that workflows execute
   * the expected steps in the correct order.
   *
   * @param workflow The workflow class to query
   * @param workflowId The unique identifier of the workflow instance
   * @return A list of step names in execution order, or an empty list if no steps were found
   */
  @ApiMayChange
  public List<String> getWorkflowSteps(Class<? extends Workflow<?>> workflow, String workflowId) {
    String componentId = getComponentId(workflow);
    List<SpanData> spanDatas =
        spansFor(componentId, AttributeKey.stringKey("akka.workflow.id"), workflowId);

    return workflowStepsFrom(spanDatas);
  }

  /**
   * Retrieves the list of agents that were invoked during an operation.
   *
   * <p>Returns a list of agent IDs in the order they were invoked, based on telemetry data
   * collected during test execution. This is useful for verifying that the expected agents were
   * called during a test scenario.
   *
   * @param debugId The debug identifier used to trace the operation
   * @return A list of agent IDs in invocation order, or an empty list if no agents were found
   */
  @ApiMayChange
  public List<String> getAgents(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    AttributeKey<String> agentToolAttributeKey = AttributeKey.stringKey("gen_ai.agent.id");

    return collectAttributeValues(spanDatas, agentToolAttributeKey);
  }

  /**
   * Retrieves the list of tools used by agents during an operation.
   *
   * <p>Returns a list of tool names in the order they were invoked, based on telemetry data
   * collected during test execution. This is useful for verifying that agents used the expected
   * tools to accomplish their tasks.
   *
   * @param debugId The debug identifier used to trace the operation
   * @return A list of tool names in invocation order, or an empty list if no tools were found
   */
  @ApiMayChange
  public List<String> getAgentTools(String debugId) {
    List<SpanData> spanDatas = spansFor(debugId);

    AttributeKey<String> agentToolAttributeKey = AttributeKey.stringKey("gen_ai.tool.name");

    return collectAttributeValues(spanDatas, agentToolAttributeKey);
  }

  /**
   * Retrieves the sequence of workflow steps executed for an operation traced by a debug ID.
   *
   * <p>Returns a list of step names in the order they were executed, based on telemetry data
   * collected during workflow execution. This method uses the debug ID to find all spans
   * associated with the traced operation, making it useful when you don't have direct access to
   * the workflow class or workflow ID.
   *
   * @param debugId The debug identifier used to trace the workflow execution
   * @return A list of step names in execution order, or an empty list if no steps were found
   */
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
