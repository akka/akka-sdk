/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.a2a;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.a2a.A2aAgents;
import akka.javasdk.a2a.A2aMessage;
import akka.javasdk.a2a.A2aRole;
import akka.javasdk.a2a.A2aSendMessageResponse;
import akka.javasdk.a2a.A2aTask;
import akka.javasdk.a2a.A2aTaskState;
import akka.javasdk.a2a.A2aTaskStatus;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.impl.client.WorkflowClientImpl;
import akka.javasdk.workflow.Workflow;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INTERNAL API
 *
 * <p>Serves the A2A methods for skills answered by task workflows: {@code SendMessage} starts the
 * workflow, or passes a later message of the task to it, and {@code CancelTask} ends it. The
 * workflow id is the task id. Each message reaches the command handler {@code
 * onMessage(A2aMessage)} of the workflow, and the workflow reports progress through {@link
 * A2aTaskContextImpl}.
 */
@InternalApi
public final class A2aTaskWorkflows {

  private static final Logger log = LoggerFactory.getLogger(A2aTaskWorkflows.class);

  /** The name of the command handler that receives the messages of a task. */
  public static final String ON_MESSAGE = "onMessage";

  private final ComponentClient componentClient;
  private final A2aEndpointSettings settings;
  private final A2aTaskStore store;

  public A2aTaskWorkflows(ComponentClient componentClient, A2aEndpointSettings settings) {
    this.componentClient = componentClient;
    this.settings = settings;
    this.store = new A2aTaskStore(componentClient);
  }

  /**
   * Checks that every workflow that injects an {@code A2aTaskContext} is published by the A2A
   * endpoint with {@code A2aAgents.taskWorkflow}, because the context answers the task of an A2A
   * client and a workflow that no endpoint publishes has no such task.
   *
   * @param contextUsers the workflows of the service that take an {@code A2aTaskContext}
   * @param published what the A2A endpoint publishes, empty when the service has no A2A endpoint
   * @throws IllegalStateException when a workflow in {@code contextUsers} is not published
   */
  public static void requirePublished(
      Collection<Class<?>> contextUsers, Optional<A2aAgents> published) {
    var publishedWorkflows = published.map(a -> a._internalTaskWorkflows()).orElse(List.of());
    for (Class<?> workflowClass : contextUsers) {
      if (!publishedWorkflows.contains(workflowClass)) {
        throw new IllegalStateException(
            "["
                + workflowClass.getName()
                + "] takes an A2aTaskContext, which is only available to a workflow that an A2A"
                + " endpoint publishes with A2aAgents.taskWorkflow("
                + workflowClass.getSimpleName()
                + ".class)"
                + (published.isPresent() ? "" : "; the service has no @A2aEndpoint"));
      }
    }
  }

  /** The component id of a task workflow. */
  public static String componentId(Class<?> workflowClass) {
    var component = workflowClass.getAnnotation(Component.class);
    if (component == null) {
      throw new IllegalStateException(
          "["
              + workflowClass.getName()
              + "] is published by the A2A endpoint, but it is not a workflow component");
    }
    onMessageMethod(workflowClass);
    return component.id();
  }

  /**
   * Starts the workflow of a new task.
   *
   * @param userMessage the first message, with the task id and context id of the new task
   * @param owner the JWT subject of the caller, or null
   * @param sessionId the agent session of the caller and the context
   */
  public A2aSendMessageResponse start(
      A2aSkillTable.Entry skill,
      A2aMessage userMessage,
      String owner,
      String sessionId,
      A2aSendMessageRequest.Configuration configuration) {
    return respond(
        startWithoutWaiting(skill, userMessage, owner, sessionId, configuration), configuration);
  }

  /**
   * Starts the workflow of a new task and returns the task once the workflow has the message, for
   * {@code SendStreamingMessage}, which follows the task from there.
   */
  public A2aTaskEntity.State startWithoutWaiting(
      A2aSkillTable.Entry skill,
      A2aMessage userMessage,
      String owner,
      String sessionId,
      A2aSendMessageRequest.Configuration configuration) {
    var taskId = userMessage.taskId();
    var submitted =
        new A2aTask(
            taskId,
            userMessage.contextId(),
            new A2aTaskStatus(A2aTaskState.SUBMITTED, null, Instant.now()),
            null,
            List.of(userMessage),
            null);
    componentClient
        .forKeyValueEntity(taskId)
        .method(A2aTaskEntity::create)
        .invoke(new A2aTaskEntity.Create(submitted, skill.skill().id(), owner, sessionId));
    if (configuration != null && configuration.taskPushNotificationConfig() != null) {
      new A2aPushConfigs(componentClient, settings.pushUrls())
          .store(taskId, configuration.taskPushNotificationConfig());
    }
    var working =
        store.change(taskId, A2aTaskEntity.Change.status(A2aTaskState.WORKING, null)).orElseThrow();
    return deliver(skill.taskWorkflow(), working, userMessage);
  }

  /**
   * Passes a later message of a task to its workflow.
   *
   * @param existing the task, which the caller may see
   * @return empty when the task is not answered by a task workflow, or has ended
   */
  public Optional<A2aSendMessageResponse> continueTask(
      A2aSkillTable table,
      A2aTask existing,
      A2aMessage message,
      A2aSendMessageRequest.Configuration configuration) {
    return continueWithoutWaiting(table, existing, message)
        .map(delivered -> respond(delivered, configuration));
  }

  /**
   * Passes a later message of a task to its workflow and returns the task once the workflow has the
   * message, for {@code SendStreamingMessage}.
   *
   * @return empty when the task is not answered by a task workflow, or has ended
   */
  public Optional<A2aTaskEntity.State> continueWithoutWaiting(
      A2aSkillTable table, A2aTask existing, A2aMessage message) {
    if (existing.status().state().terminal()) return Optional.empty();
    var state = store.get(existing.id()).orElseThrow();
    var skill = table.find(state.skillId()).filter(e -> e.taskWorkflow() != null);
    if (skill.isEmpty()) return Optional.empty();
    var userMessage =
        new A2aMessage(
            message.messageId(),
            existing.contextId(),
            existing.id(),
            A2aRole.USER,
            message.parts(),
            message.metadata(),
            message.extensions(),
            message.referenceTaskIds());
    var changed =
        store.change(existing.id(), new A2aTaskEntity.Change(userMessage, null, null, null, true));
    if (changed.isEmpty()) return Optional.empty();
    return Optional.of(deliver(skill.get().taskWorkflow(), changed.get(), userMessage));
  }

  /**
   * Cancels a task answered by a task workflow: ends the workflow and marks the task canceled.
   *
   * @param existing the task, which the caller may see
   * @return empty when the task is not answered by a task workflow
   * @throws A2aException TASK_NOT_CANCELABLE when the task has ended
   */
  public Optional<A2aTask> cancel(A2aSkillTable table, A2aTask existing) {
    var state = store.get(existing.id()).orElseThrow();
    var skill = table.find(state.skillId()).filter(e -> e.taskWorkflow() != null);
    if (skill.isEmpty()) return Optional.empty();
    if (existing.status().state().terminal()) {
      throw new A2aException(
          A2aErrorCode.TASK_NOT_CANCELABLE,
          "Task [" + existing.id() + "] has ended and cannot be canceled");
    }
    try {
      componentClient
          .forWorkflow(existing.id())
          .terminate(skill.get().taskWorkflow(), "Canceled by the A2A client");
    } catch (RuntimeException e) {
      // The workflow may have ended on its own without ending the task
      log.debug("Could not terminate the workflow of A2A task [{}]", existing.id(), e);
    }
    var result =
        componentClient.forKeyValueEntity(existing.id()).method(A2aTaskEntity::cancel).invoke();
    if (result.canceled()) {
      A2aPushNotifier.taskChanged(componentClient, result.state()).toCompletableFuture().join();
    }
    return Optional.of(result.state().task());
  }

  // ---------------------------------------------------------------------------------------------

  // Passes the message to onMessage. A message that the workflow refuses fails the task.
  private A2aTaskEntity.State deliver(
      Class<? extends Workflow<?>> workflowClass,
      A2aTaskEntity.State delivered,
      A2aMessage userMessage) {
    var taskId = delivered.task().id();
    try {
      callOnMessage(workflowClass, taskId, userMessage);
      return delivered;
    } catch (RuntimeException e) {
      log.warn("The workflow of A2A task [{}] refused the message", taskId, e);
      var reason =
          A2aTaskStore.agentMessage(
              taskId,
              delivered.task().contextId(),
              List.of(akka.javasdk.a2a.A2aPart.text("The agent could not accept the message.")));
      return store
          .change(taskId, A2aTaskEntity.Change.status(A2aTaskState.FAILED, reason))
          .orElse(delivered);
    }
  }

  private A2aSendMessageResponse respond(
      A2aTaskEntity.State delivered, A2aSendMessageRequest.Configuration configuration) {
    var taskId = delivered.task().id();
    if (delivered.task().status().state().terminal()
        || (configuration != null && configuration.returnImmediately())) {
      return A2aSendMessageResponse.of(trim(delivered.task(), configuration));
    }
    var reported = awaitInterruptedOrEnded(taskId, delivered.version());
    if (reported.task().status().state() == A2aTaskState.COMPLETED) {
      var directReply =
          componentClient
              .forKeyValueEntity(taskId)
              .method(A2aTaskExtrasEntity::get)
              .invoke()
              .directReply();
      if (directReply != null) return A2aSendMessageResponse.of(directReply);
    }
    return A2aSendMessageResponse.of(trim(reported.task(), configuration));
  }

  private void callOnMessage(
      Class<? extends Workflow<?>> workflowClass, String taskId, A2aMessage message) {
    var client = (WorkflowClientImpl) componentClient.forWorkflow(taskId);
    client.<A2aMessage, Done>methodRefOneArg(onMessageMethod(workflowClass)).invoke(message);
  }

  // Polls the task until it ends or asks for input, or until the blocking wait has passed
  private A2aTaskEntity.State awaitInterruptedOrEnded(String taskId, long afterVersion) {
    var deadline = System.nanoTime() + settings.blockingWait().toNanos();
    long pause = 10;
    while (true) {
      var current = store.get(taskId).orElseThrow();
      var taskState = current.task().status().state();
      boolean settled =
          current.version() > afterVersion
              && (taskState.terminal()
                  || taskState == A2aTaskState.INPUT_REQUIRED
                  || taskState == A2aTaskState.AUTH_REQUIRED);
      if (settled || System.nanoTime() > deadline) return current;
      try {
        Thread.sleep(pause);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return current;
      }
      pause = Math.min(pause * 2, 200);
    }
  }

  private static A2aTask trim(A2aTask task, A2aSendMessageRequest.Configuration configuration) {
    Integer historyLength = configuration == null ? null : configuration.historyLength();
    if (historyLength == null || task.history().size() <= historyLength) return task;
    var history = task.history();
    return new A2aTask(
        task.id(),
        task.contextId(),
        task.status(),
        task.artifacts(),
        history.subList(history.size() - historyLength, history.size()),
        task.metadata());
  }

  private static Method onMessageMethod(Class<?> workflowClass) {
    try {
      var method = workflowClass.getMethod(ON_MESSAGE, A2aMessage.class);
      if (!Workflow.Effect.class.isAssignableFrom(method.getReturnType())) {
        throw new NoSuchMethodException();
      }
      return method;
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "Task workflow ["
              + workflowClass.getName()
              + "] has no command handler public Effect<Done> onMessage(A2aMessage message)");
    }
  }
}
