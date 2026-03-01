/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.agent;

import akka.Done;
import akka.annotation.InternalApi;
import akka.javasdk.agent.autonomous.AutonomousAgentWorkflow;
import akka.javasdk.agent.task.TaskClient;
import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.agent.task.TaskNotification;
import akka.javasdk.agent.task.TaskNotificationWorkflow;
import akka.javasdk.agent.task.TaskRef;
import akka.javasdk.agent.task.TaskSnapshot;
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
public final class TaskClientImpl<R> implements TaskClient<R> {

  private static final Logger log = LoggerFactory.getLogger(TaskClientImpl.class);

  private static final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

  private final ComponentClient componentClient;
  private final String taskId;
  private final String description;
  private final Class<R> resultType;

  public TaskClientImpl(ComponentClient componentClient, TaskRef<R> ref) {
    this.componentClient = componentClient;
    this.taskId = ref.taskId();
    this.description = ref.description();
    this.resultType = ref.resultType();
  }

  @Override
  public Done create() {
    return create(null, List.of());
  }

  @Override
  public Done create(String instructions) {
    return create(instructions, List.of());
  }

  @Override
  public Done create(String instructions, List<String> dependencyTaskIds) {
    componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::create)
        .invoke(
            new TaskEntity.CreateRequest(
                description, instructions, resultType.getName(), dependencyTaskIds));
    return Done.done();
  }

  @Override
  @SuppressWarnings("unchecked")
  public TaskSnapshot<R> get() {
    TaskState state =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

    R typedResult = null;
    if (state.result() != null && !state.result().isEmpty()) {
      if (resultType == String.class) {
        typedResult = (R) state.result();
      } else {
        try {
          typedResult = objectMapper.readValue(state.result(), resultType);
        } catch (Exception e) {
          log.warn(
              "Failed to deserialize task result to {}: {}",
              resultType.getSimpleName(),
              e.getMessage());
        }
      }
    }

    return new TaskSnapshot<>(
        state.status(),
        state.description(),
        state.instructions(),
        typedResult,
        state.pendingDecisionId(),
        state.pendingDecisionQuestion());
  }

  @Override
  public Done provideInput(String decisionId, String response) {
    componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::provideInput)
        .invoke(new TaskEntity.InputResponse(decisionId, response));

    var taskState =
        componentClient.forEventSourcedEntity(taskId).method(TaskEntity::getState).invoke();

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

  @Override
  public ComponentStreamMethodRef<TaskNotification> notifications() {
    return componentClient
        .forWorkflow(taskId)
        .notificationStream(TaskNotificationWorkflow::updates);
  }
}
