/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.Task;

public class TestTasks {

  public record TestResult(String value, int score) {}

  public static final Task<TestResult> TEST_TASK =
      Task.define("Test task").description("A test task").resultConformsTo(TestResult.class);

  public static final Task<String> STRING_TASK =
      Task.define("String task").description("A task with string result");
}
