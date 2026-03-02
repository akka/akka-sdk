package demo.pipeline.application;

import akka.javasdk.agent.task.Task;

public class PipelineTasks {

  public static final Task<ReportResult> COLLECT = Task.of(
    "Collect data",
    ReportResult.class
  );

  public static final Task<ReportResult> ANALYZE = Task.of(
    "Analyze collected data",
    ReportResult.class
  );

  public static final Task<ReportResult> REPORT = Task.of(
    "Write final report",
    ReportResult.class
  );
}
