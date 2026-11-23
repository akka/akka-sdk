/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.evaluation.Subject;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import akkajavasdk.components.tracing.SpanRecorder;
import akkajavasdk.components.tracing.SpanRecordingAgent;
import akkajavasdk.components.tracing.SpanRecordingDurableEvaluator;
import akkajavasdk.components.tracing.SpanRecordingEntity;
import akkajavasdk.components.tracing.SpanRecordingEvaluator;
import akkajavasdk.components.tracing.SpanRecordingWorkflow;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The runtime span of a call is the current span in the user code that handles it. */
@ExtendWith(Junit5LogCapturing.class)
public class CurrentSpanScopesTest extends TestKitSupport {

  private final TestModelProvider testModelProvider = new TestModelProvider();

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withModelProvider(SpanRecordingAgent.class, testModelProvider);
  }

  private SpanContext awaitRecorded(String key) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(SpanRecorder.recorded(key)).isNotNull());
    return SpanRecorder.recorded(key);
  }

  // the recorded span is an exported runtime span with the expected name
  private void assertCurrentSpanNamed(SpanContext recorded, String expectedName) {
    assertThat(recorded.isValid()).isTrue();
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(testKit.getInMemorySpanExporter().getFinishedSpanItems())
                    .filteredOn(
                        span ->
                            span.getTraceId().equals(recorded.getTraceId())
                                && span.getSpanId().equals(recorded.getSpanId()))
                    .extracting(SpanData::getName)
                    .containsExactly(expectedName));
  }

  // a span with the given name is exported as a child of the recorded span
  private void assertChildSpanNamed(SpanContext recorded, String childName) {
    Awaitility.await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(testKit.getInMemorySpanExporter().getFinishedSpanItems())
                    .filteredOn(
                        span ->
                            span.getTraceId().equals(recorded.getTraceId())
                                && span.getName().equals(childName))
                    .extracting(SpanData::getParentSpanId)
                    .containsExactly(recorded.getSpanId()));
  }

  @Test
  public void entityCommandHandlerHasTheCurrentSpan() {
    var id = UUID.randomUUID().toString();
    componentClient.forEventSourcedEntity(id).method(SpanRecordingEntity::record).invoke();

    assertCurrentSpanNamed(
        awaitRecorded("entity:" + id), "Event Sourced Entity: span-recording-entity Record");
  }

  @Test
  public void consumerHasTheCurrentSpan() {
    var id = UUID.randomUUID().toString();
    componentClient.forEventSourcedEntity(id).method(SpanRecordingEntity::record).invoke();

    assertCurrentSpanNamed(awaitRecorded("consumer:" + id), "Consumer: span-recording-consumer");
  }

  @Test
  public void viewUpdaterHasTheCurrentSpan() {
    var id = UUID.randomUUID().toString();
    componentClient.forEventSourcedEntity(id).method(SpanRecordingEntity::record).invoke();

    // the view updater runs in the span of the event, the one that wrote it
    var recorded = awaitRecorded("view:" + id);
    assertThat(recorded.isValid()).isTrue();
    assertThat(recorded.getTraceId()).isEqualTo(awaitRecorded("entity:" + id).getTraceId());
    assertCurrentSpanNamed(recorded, "Event Sourced Entity: span-recording-entity Record");
  }

  @Test
  public void workflowStepHasTheCurrentSpan() {
    var id = UUID.randomUUID().toString();
    componentClient.forWorkflow(id).method(SpanRecordingWorkflow::start).invoke();

    assertCurrentSpanNamed(
        awaitRecorded("workflow:" + id), "Workflow: span-recording-workflow recordStep");
  }

  @Test
  public void agentFunctionToolHasTheCurrentSpan() {
    var question = "Record the span " + UUID.randomUUID();
    testModelProvider
        .whenUserMessage(msg -> msg.content().equals(question))
        .reply(new ToolInvocationRequest("SpanRecordingAgent_recordSpan", ""));
    testModelProvider
        .whenToolResult(result -> result.content().equals("recorded"))
        .thenReply(result -> new AiResponse("done"));

    componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(SpanRecordingAgent::query)
        .invoke(question);

    assertCurrentSpanNamed(awaitRecorded("agent-tool"), "Tool call: SpanRecordingAgent_recordSpan");
  }

  @Test
  public void evaluatorHasTheCurrentSpan() {
    var key = UUID.randomUUID().toString();
    componentClient
        .forEvaluator(SpanRecordingEvaluator.class)
        .startEvaluation(new Subject.Workflow(key, "span-recording-workflow"), Map.of("key", key));

    var recorded = awaitRecorded("evaluator:" + key);
    assertCurrentSpanNamed(recorded, "Evaluation: span-recording-evaluator");
    assertChildSpanNamed(recorded, SpanRecordingEvaluator.CUSTOM_SPAN);
  }

  @Test
  public void durableEvaluatorStartAndStepHaveTheCurrentSpan() {
    var key = UUID.randomUUID().toString();
    componentClient
        .forEvaluator(SpanRecordingDurableEvaluator.class)
        .startEvaluation(new Subject.Workflow(key, "span-recording-workflow"), Map.of("key", key));

    var start = awaitRecorded("durable-evaluator-start:" + key);
    assertCurrentSpanNamed(start, "Workflow: span-recording-durable-evaluator _evaluation-start");
    assertChildSpanNamed(start, SpanRecordingDurableEvaluator.CUSTOM_START_SPAN);

    var step = awaitRecorded("durable-evaluator-step:" + key);
    assertCurrentSpanNamed(step, "Workflow: span-recording-durable-evaluator recordStep");
    assertChildSpanNamed(step, SpanRecordingDurableEvaluator.CUSTOM_STEP_SPAN);
    assertThat(step.getTraceId()).isEqualTo(start.getTraceId());
  }
}
