/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskTemplate;

public class TestTasks {

  public record TestResult(String value, int score) {}

  public record ResearchResult(String title, String summary) {}

  public record FindingsResult(String topic, String findings) {}

  public record SupportResolution(String category, String resolution, boolean resolved) {}

  public record PlanResult(String summary, int tasksCompleted) {}

  public record WorkItemResult(String item, String output) {}

  public record ModerationResult(String topic, String conclusion) {}

  public static final Task<TestResult> TEST_TASK =
      Task.name("Test task").description("A test task").resultConformsTo(TestResult.class);

  public static final Task<String> STRING_TASK =
      Task.name("String task").description("A task with string result");

  public static final Task<Integer> INTEGER_TASK =
      Task.name("Integer task")
          .description("A task with integer result")
          .resultConformsTo(Integer.class);

  public static final Task<Boolean> BOOLEAN_TASK =
      Task.name("Boolean task")
          .description("A task with boolean result")
          .resultConformsTo(Boolean.class);

  public static final Task<ResearchResult> RESEARCH =
      Task.name("Research")
          .description("Produce a research summary")
          .resultConformsTo(ResearchResult.class);

  public static final Task<FindingsResult> FINDINGS =
      Task.name("Findings")
          .description("Research a topic and produce findings")
          .resultConformsTo(FindingsResult.class);

  public static final Task<SupportResolution> RESOLVE =
      Task.name("Resolve")
          .description("Resolve a support request")
          .resultConformsTo(SupportResolution.class);

  public static final Task<PlanResult> PLAN =
      Task.name("Plan").description("Plan and coordinate work").resultConformsTo(PlanResult.class);

  public static final TaskTemplate<WorkItemResult> WORK_ITEM =
      TaskTemplate.define("Work item")
          .description("Implement a work item")
          .resultConformsTo(WorkItemResult.class)
          .instructionTemplate("Implement: {item}. Requirements: {requirements}.");

  public static final Task<ModerationResult> MODERATE =
      Task.name("Moderate")
          .description("Moderate a conversation")
          .resultConformsTo(ModerationResult.class);

  public static final Task<TestResult> VALIDATED_TASK =
      Task.name("Validated task")
          .description("A task with rule validation")
          .resultConformsTo(TestResult.class)
          .rules(TestResultRule.class);
}
