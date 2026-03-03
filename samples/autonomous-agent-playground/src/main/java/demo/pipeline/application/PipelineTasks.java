/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline.application;

import akka.javasdk.agent.task.Task;

/** Task definitions for the 3-phase report pipeline: collect → analyze → report. */
public class PipelineTasks {

  // prettier-ignore
  public static final Task<ReportResult> COLLECT = Task
    .define("Collect")
    .description("Collect data on a topic")
    .resultConformsTo(ReportResult.class);

  // prettier-ignore
  public static final Task<ReportResult> ANALYZE = Task
    .define("Analyze")
    .description("Analyze collected data")
    .resultConformsTo(ReportResult.class);

  // prettier-ignore
  public static final Task<ReportResult> REPORT = Task
    .define("Report")
    .description("Write final report")
    .resultConformsTo(ReportResult.class);
}
