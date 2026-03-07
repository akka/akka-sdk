package demo.pipeline.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import demo.pipeline.application.PipelineTasks;
import demo.pipeline.application.ReportAgent;
import demo.pipeline.application.ReportResult;
import java.util.List;
import java.util.UUID;

/**
 * Multi-phase report pipeline with task dependencies.
 *
 * <p>Creates 3 tasks in a dependency chain: collect → analyze → report. The agent processes them in
 * order — dependent tasks are automatically re-queued until their dependencies complete.
 *
 * <p>Usage:
 *
 * <pre>
 * # Create a pipeline
 * curl -X POST localhost:9000/pipeline -H "Content-Type: application/json" \
 *   -d '{"topic": "Cloud Computing Market"}'
 *
 * # Check status of all phases
 * curl localhost:9000/pipeline/all/{pipelineId}
 *
 * # Check a specific task
 * curl localhost:9000/pipeline/{taskId}
 * </pre>
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/pipeline")
public class PipelineEndpoint {

  public record CreatePipeline(String topic) {}

  public record PipelineResponse(
    String pipelineId,
    String collectTaskId,
    String analyzeTaskId,
    String reportTaskId
  ) {}

  public record TaskStatusResponse(String status, ReportResult result) {}

  public record PipelineStatusResponse(
    TaskStatusResponse collect,
    TaskStatusResponse analyze,
    TaskStatusResponse report
  ) {}

  private final ComponentClient componentClient;

  public PipelineEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post
  public PipelineResponse create(CreatePipeline request) {
    var pipelineId = UUID.randomUUID().toString().substring(0, 8);
    var collectId = "pipeline-" + pipelineId + "-collect";
    var analyzeId = "pipeline-" + pipelineId + "-analyze";
    var reportId = "pipeline-" + pipelineId + "-report";

    // Create tasks with dependency chain: collect → analyze → report
    componentClient
      .forTask(PipelineTasks.COLLECT.ref(collectId))
      .create("Collect data on: " + request.topic());

    componentClient
      .forTask(PipelineTasks.ANALYZE.ref(analyzeId))
      .create(
        "Analyze the collected data for: " + request.topic() + ". Use the analyzeData tool.",
        List.of(collectId)
      );

    componentClient
      .forTask(PipelineTasks.REPORT.ref(reportId))
      .create(
        "Write a comprehensive final report for: " +
        request.topic() +
        ". Synthesize findings into a clear executive summary.",
        List.of(analyzeId)
      );

    // Start an agent and assign all 3 tasks
    var agentId = "agent-" + pipelineId;
    var agentClient = componentClient.forAutonomousAgent(agentId, ReportAgent.class);
    agentClient.start();
    agentClient.assignTask(collectId);
    agentClient.assignTask(analyzeId);
    agentClient.assignTask(reportId);

    return new PipelineResponse(pipelineId, collectId, analyzeId, reportId);
  }

  @Get("/{id}")
  public TaskStatusResponse get(String id) {
    var task = componentClient.forTask(PipelineTasks.COLLECT.ref(id)).get();
    return new TaskStatusResponse(task.status().name(), task.result());
  }

  @Get("/all/{pipelineId}")
  public PipelineStatusResponse getAll(String pipelineId) {
    return new PipelineStatusResponse(
      getTaskStatus("pipeline-" + pipelineId + "-collect"),
      getTaskStatus("pipeline-" + pipelineId + "-analyze"),
      getTaskStatus("pipeline-" + pipelineId + "-report")
    );
  }

  private TaskStatusResponse getTaskStatus(String taskId) {
    var task = componentClient.forTask(PipelineTasks.COLLECT.ref(taskId)).get();
    return new TaskStatusResponse(task.status().name(), task.result());
  }
}
