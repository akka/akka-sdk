/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import akkajavasdk.components.tracecontext.SpanIds;
import akkajavasdk.components.tracecontext.TraceContextAction;
import akkajavasdk.components.tracecontext.TraceContextAgent;
import akkajavasdk.components.tracecontext.TraceContextConsumer;
import akkajavasdk.components.tracecontext.TraceContextEsEntity;
import akkajavasdk.components.tracecontext.TraceContextKvEntity;
import akkajavasdk.components.tracecontext.TraceContextWorkflow;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(Junit5LogCapturing.class)
public class TraceContextTest extends TestKitSupport {

  private static String newId() {
    return UUID.randomUUID().toString();
  }

  private static void assertCurrentIsParent(SpanIds spanIds) {
    assertThat(spanIds.parent()).isNotEqualTo(SpanIds.NONE);
    assertThat(spanIds.current()).isEqualTo(spanIds.parent());
  }

  private static void assertCurrentAndTaskAreParent(SpanIds spanIds) {
    assertCurrentIsParent(spanIds);
    assertThat(spanIds.inTask()).isEqualTo(spanIds.parent());
  }

  @Test
  public void shouldPassRequestSpanToExecutorTaskInHttpEndpoint() {
    var spanIds = httpClient.GET("/trace-context/observe").responseBodyAs(SpanIds.class).invoke();
    assertCurrentAndTaskAreParent(spanIds.body());
  }

  @Test
  public void shouldPassCommandSpanToExecutorTaskInTimedAction() {
    var id = newId();
    timerScheduler.createSingleTimer(
        id,
        Duration.ZERO,
        componentClient.forTimedAction().method(TraceContextAction::observe).deferred(id));
    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(TraceContextAction.observed.get(id)).isNotNull());
    assertCurrentAndTaskAreParent(TraceContextAction.observed.get(id));
  }

  @Test
  public void shouldMakeCommandSpanCurrentInKeyValueEntity() {
    var spanIds =
        componentClient.forKeyValueEntity(newId()).method(TraceContextKvEntity::observe).invoke();
    assertCurrentIsParent(spanIds);
  }

  @Test
  public void shouldMakeCommandSpanCurrentInEventSourcedEntity() {
    var spanIds =
        componentClient
            .forEventSourcedEntity(newId())
            .method(TraceContextEsEntity::observe)
            .invoke();
    assertCurrentIsParent(spanIds);
  }

  @Test
  public void shouldPassMessageSpanToExecutorTaskInConsumer() {
    var entityId = newId();
    componentClient.forKeyValueEntity(entityId).method(TraceContextKvEntity::observe).invoke();
    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(TraceContextConsumer.observed.get(entityId)).isNotNull());
    assertCurrentAndTaskAreParent(TraceContextConsumer.observed.get(entityId));
  }

  @Test
  public void shouldPassSpanToExecutorTaskInWorkflowCommandAndStep() {
    var workflowId = newId();
    componentClient.forWorkflow(workflowId).method(TraceContextWorkflow::start).invoke();
    Awaitility.await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var state =
                  componentClient
                      .forWorkflow(workflowId)
                      .method(TraceContextWorkflow::get)
                      .invoke();
              assertThat(state.inStep()).isNotNull();
            });
    var state = componentClient.forWorkflow(workflowId).method(TraceContextWorkflow::get).invoke();
    assertCurrentAndTaskAreParent(state.inCommand());
    assertCurrentAndTaskAreParent(state.inStep());
  }

  @Test
  public void shouldPassCommandSpanToExecutorTaskInAgent() {
    var spanIds =
        componentClient.forAgent().inSession(newId()).method(TraceContextAgent::observe).invoke();
    assertCurrentAndTaskAreParent(spanIds);
  }
}
