/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.agent.autonomous.AutonomousAgentWorkflow;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskNotification;
import akka.javasdk.agent.task.TaskNotificationWorkflow;
import akka.javasdk.agent.task.TaskState;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.client.ComponentStreamMethodRef;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** INTERNAL USE Not for user extension or instantiation */
@InternalApi
public final class TaskClient<R> implements Task<R> {

  private static final Logger log = LoggerFactory.getLogger(TaskClient.class);

  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  private final ComponentClient componentClient;
  private final String taskId;
  private final Class<R> resultType;

  public TaskClient(ComponentClient componentClient, String taskId, Class<R> resultType) {
    this.componentClient = componentClient;
    this.taskId = taskId;
    this.resultType = resultType;
  }

  @Override
  public Done create(String description, List<String> dependencyTaskIds) {
    return componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::create)
        .invoke(new TaskEntity.CreateRequest(description, resultType.getName(), dependencyTaskIds));
  }

  @Override
  public TaskState getState() {
    return componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();
  }

  @Override
  @SuppressWarnings("unchecked")
  public R getResult() {
    var state = getState();
    if (state.result() == null || state.result().isEmpty()) {
      return null;
    }
    if (resultType == String.class) {
      return (R) state.result();
    }
    try {
      return objectMapper.readValue(state.result(), resultType);
    } catch (Exception e) {
      log.warn(
          "Failed to deserialize task result to {}: {}",
          resultType.getSimpleName(),
          e.getMessage());
      return null;
    }
  }

  @Override
  public ComponentStreamMethodRef<TaskNotification> notifications() {
    return componentClient
        .forWorkflow(taskId)
        .notificationStream(TaskNotificationWorkflow::updates);
  }

  @Override
  public Done provideInput(String decisionId, String response) {
    // Write the input to the task entity
    componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::provideInput)
        .invoke(new TaskEntity.InputResponse(decisionId, response));

    // Read the task state to discover which workflow (agent) is processing this task
    var taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    // Resume the workflow — the assignee field holds the workflow ID
    var assignee = taskState.assignee();
    if (assignee != null && !assignee.isEmpty()) {
      componentClient
          .forWorkflow(assignee)
          .method(AutonomousAgentWorkflow::resumeAfterInput)
          .invoke();
    } else {
      log.warn("Task {} has no assignee — cannot resume workflow after input", taskId);
    }

    return Done.done();
  }
}
