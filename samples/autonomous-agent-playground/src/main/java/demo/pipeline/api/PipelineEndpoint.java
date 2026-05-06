/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.pipeline.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import demo.pipeline.application.PipelineTasks;
import demo.pipeline.application.ReportAgent;
import demo.pipeline.application.ReportResult;
import java.util.UUID;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/pipeline")
public class PipelineEndpoint extends AbstractHttpEndpoint {

  public record CreatePipeline(String topic) {}

  public record PipelineResponse(
    String pipelineId,
    String collectTaskId,
    String analyzeTaskId,
    String reportTaskId,
    String runId,
    String agentComponentId
  ) {}

  public record PhaseStatus(String status, ReportResult result) {}

  private final ComponentClient componentClient;

  public PipelineEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public PipelineResponse create(CreatePipeline request) {
    // tag::create-with-deps[]
    // Create collect task (no dependencies)
    var collectTaskId = componentClient
      .forTask(UUID.randomUUID().toString())
      .create(PipelineTasks.COLLECT.instructions("Collect data on: " + request.topic()));

    // Create analyze task (depends on collect)
    var analyzeTaskId = componentClient
      .forTask(UUID.randomUUID().toString())
      .create(
        PipelineTasks.ANALYZE.instructions("Analyze data for: " + request.topic()).dependsOn(
          collectTaskId
        )
      );

    // Create report task (depends on analyze)
    var reportTaskId = componentClient
      .forTask(UUID.randomUUID().toString())
      .create(
        PipelineTasks.REPORT.instructions("Write report for: " + request.topic()).dependsOn(
          analyzeTaskId
        )
      );
    // end::create-with-deps[]

    // Assign all tasks to a single agent instance — accept an optional pre-generated runId so the
    // UI can subscribe to the notification stream before the agent activates.
    var agentInstanceId = requestContext()
      .queryParams()
      .getString("runId")
      .filter(s -> !s.isBlank())
      .orElseGet(() -> UUID.randomUUID().toString());
    // tag::assign-tasks[]
    componentClient
      .forAutonomousAgent(ReportAgent.class, agentInstanceId)
      .assignTasks(collectTaskId, analyzeTaskId, reportTaskId);
    // end::assign-tasks[]

    return new PipelineResponse(
      agentInstanceId,
      collectTaskId,
      analyzeTaskId,
      reportTaskId,
      agentInstanceId,
      "report-agent"
    );
  }

  @Get("/{taskId}")
  public PhaseStatus getPhaseStatus(String taskId) {
    var snapshot = componentClient.forTask(taskId).get(PipelineTasks.COLLECT);
    return new PhaseStatus(snapshot.status().name(), snapshot.result());
  }
}
