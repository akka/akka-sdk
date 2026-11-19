/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.agent.task;

import static akkajavasdk.components.agent.autonomous.TestTasks.TEST_TASK;
import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.agent.task.TaskEntity;
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class TaskExpiryIntegrationTest extends TestKitSupport {

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
        "akka.javasdk.agent.task.expire-after = 1 hour");
  }

  private Duration storedExpireAfter(String taskId) {
    return componentClient
        .forEventSourcedEntity(taskId)
        .method(TaskEntity::getState)
        .invoke()
        .expireAfter()
        .orElseThrow();
  }

  @Test
  public void shouldSetTheDefaultExpiryOnTaskWithoutOne() {
    var taskId = UUID.randomUUID().toString();
    componentClient.forTask(taskId).create(TEST_TASK.instructions("do something"));

    assertThat(storedExpireAfter(taskId)).isEqualTo(Duration.ofHours(1));
  }

  @Test
  public void shouldKeepTheExpiryOfTheTask() {
    var taskId = UUID.randomUUID().toString();
    componentClient
        .forTask(taskId)
        .create(TEST_TASK.expireAfter(Duration.ofMinutes(10)).instructions("do something"));

    assertThat(storedExpireAfter(taskId)).isEqualTo(Duration.ofMinutes(10));
  }
}
