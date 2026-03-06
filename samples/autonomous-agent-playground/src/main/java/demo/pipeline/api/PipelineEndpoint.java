/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.pipeline.application.PipelineTasks;
import demo.pipeline.application.ReportAgent;
import demo.pipeline.application.ReportResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/pipeline")
public class PipelineEndpoint {

  public record CreatePipeline(String topic) {}

  public record PipelineResponse(
    String pipelineId,
    String collectId,
    String analyzeId,
    String reportId
  ) {}

  public record PhaseStatus(String status, ReportResult result) {}

  public record PipelineStatusResponse(
    PhaseStatus collect,
    PhaseStatus analyze,
    PhaseStatus report
  ) {}

  private final ComponentClient componentClient;

  public PipelineEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public PipelineResponse create(CreatePipeline request) {
    // Create collect task (no dependencies)
    // prettier-ignore
    var collectId = componentClient
      .forTask(PipelineTasks.COLLECT)
      .create(PipelineTasks.COLLECT
        .instructions("Collect data on: " + request.topic()));

    // Create analyze task (depends on collect)
    // prettier-ignore
    var analyzeId = componentClient
      .forTask(PipelineTasks.ANALYZE)
      .create(PipelineTasks.ANALYZE
        .instructions("Analyze data for: " + request.topic())
        .dependsOn(collectId));

    // Create report task (depends on analyze)
    // prettier-ignore
    var reportId = componentClient
      .forTask(PipelineTasks.REPORT)
      .create(PipelineTasks.REPORT
        .instructions("Write report for: " + request.topic())
        .dependsOn(analyzeId));

    // Assign all tasks to a single agent instance
    var agentInstanceId = UUID.randomUUID().toString();
    componentClient
      .forAutonomousAgent(ReportAgent.class, agentInstanceId)
      .assignTasks(collectId, analyzeId, reportId);

    return new PipelineResponse(agentInstanceId, collectId, analyzeId, reportId);
  }

  @Get("/all/{pipelineId}")
  public PipelineStatusResponse getStatus(String pipelineId) {
    // This is a convenience endpoint; in practice you'd track the task IDs
    // For the integration test, we'll query individual tasks by ID instead
    throw new UnsupportedOperationException(
      "Use GET /pipeline/{taskId} to query individual task status"
    );
  }

  @Get("/{taskId}")
  public PhaseStatus getPhaseStatus(String taskId) {
    var snapshot = componentClient.forTask(PipelineTasks.COLLECT).get(taskId);
    return new PhaseStatus(snapshot.status().name(), snapshot.result());
  }
}
