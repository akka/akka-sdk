/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aArtifact;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aPart;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aTaskContext;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import java.util.UUID;

/**
 * INTERNAL API
 *
 * <p>The {@link A2aTaskContext} injected into a workflow. The workflow id is the task id.
 */
@InternalApi
public final class A2aTaskContextImpl implements A2aTaskContext {

  private final String taskId;
  private final ComponentClient componentClient;
  private final A2aTaskStore store;
  private volatile String contextId;
  private volatile String sessionId;

  public A2aTaskContextImpl(String taskId, ComponentClient componentClient) {
    this.taskId = taskId;
    this.componentClient = componentClient;
    this.store = new A2aTaskStore(componentClient);
  }

  @Override
  public String taskId() {
    return taskId;
  }

  @Override
  public String contextId() {
    if (contextId == null) contextId = storedTask().task().contextId();
    return contextId;
  }

  @Override
  public String sessionId() {
    if (sessionId == null) {
      var state = storedTask();
      contextId = state.task().contextId();
      sessionId =
          state.sessionId() != null
              ? state.sessionId()
              // A task stored without a session id, before the field existed, has no issuer
              : A2aSessions.sessionId(null, state.owner(), contextId);
    }
    return sessionId;
  }

  private A2aTaskEntity.State storedTask() {
    return store
        .get(taskId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Workflow ["
                        + taskId
                        + "] does not answer an A2A task. A2aTaskContext is for workflows"
                        + " that an A2A endpoint starts."));
  }

  @Override
  public void working(String text) {
    var message = agentMessage(text);
    store.change(taskId, A2aTaskEntity.Change.status(A2aTaskState.WORKING, message));
  }

  @Override
  public void artifact(A2aArtifact artifact) {
    var withId =
        artifact.artifactId() == null || artifact.artifactId().isBlank()
            ? new A2aArtifact(
                UUID.randomUUID().toString(),
                artifact.name(),
                artifact.description(),
                artifact.parts(),
                artifact.metadata(),
                artifact.extensions())
            : artifact;
    store.change(taskId, A2aTaskEntity.Change.artifact(withId));
  }

  @Override
  public void inputRequired(String question) {
    var message = agentMessage(question);
    store.change(
        taskId,
        new A2aTaskEntity.Change(message, null, A2aTaskState.INPUT_REQUIRED, message, false));
  }

  @Override
  public void completed(A2aMessage message) {
    finish(A2aTaskState.COMPLETED, message);
  }

  @Override
  public void completed(String text) {
    finish(A2aTaskState.COMPLETED, agentMessage(text));
  }

  @Override
  public void failed(String reason) {
    finish(A2aTaskState.FAILED, agentMessage(reason));
  }

  @Override
  public void respondWithMessage(A2aMessage message) {
    var reply = inTask(message);
    componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskExtrasEntity::setDirectReply)
        .invoke(reply);
    finish(A2aTaskState.COMPLETED, reply);
  }

  @Override
  public A2aMessage agentMessage(String text) {
    return A2aTaskStore.agentMessage(taskId, contextId(), List.of(A2aPart.text(text)));
  }

  private void finish(A2aTaskState state, A2aMessage message) {
    var inTask = inTask(message);
    store.change(taskId, new A2aTaskEntity.Change(inTask, null, state, inTask, false));
  }

  private A2aMessage inTask(A2aMessage message) {
    return new A2aMessage(
        message.messageId() == null || message.messageId().isBlank()
            ? UUID.randomUUID().toString()
            : message.messageId(),
        contextId(),
        taskId,
        message.role() == null || message.role() == A2aRole.UNSPECIFIED
            ? A2aRole.AGENT
            : message.role(),
        message.parts(),
        message.metadata(),
        message.extensions(),
        message.referenceTaskIds());
  }
}
