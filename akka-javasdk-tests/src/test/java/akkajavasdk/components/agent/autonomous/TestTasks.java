/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akkajavasdk.components.agent.autonomous;

import akka.javasdk.agent.task.Task;

public class TestTasks {

  public record TestResult(String value, int score) {}

  public record ResearchResult(String title, String summary) {}

  public record FindingsResult(String topic, String findings) {}

  public record SupportResolution(String category, String resolution, boolean resolved) {}

  public static final Task<TestResult> TEST_TASK =
      Task.define("Test task").description("A test task").resultConformsTo(TestResult.class);

  public static final Task<String> STRING_TASK =
      Task.define("String task").description("A task with string result");

  public static final Task<ResearchResult> RESEARCH =
      Task.define("Research")
          .description("Produce a research summary")
          .resultConformsTo(ResearchResult.class);

  public static final Task<FindingsResult> FINDINGS =
      Task.define("Findings")
          .description("Research a topic and produce findings")
          .resultConformsTo(FindingsResult.class);

  public static final Task<SupportResolution> RESOLVE =
      Task.define("Resolve")
          .description("Resolve a support request")
          .resultConformsTo(SupportResolution.class);
}
