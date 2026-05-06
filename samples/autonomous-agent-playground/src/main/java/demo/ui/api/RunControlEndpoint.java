package demo.ui.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.agent.task.Task;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpException;
import demo.consulting.application.ConsultingTasks;
import demo.debate.application.DebateTasks;
import demo.devteam.application.ProjectTasks;
import demo.dynamic.application.DynamicTasks;
import demo.helloworld.application.QuestionTasks;
import demo.negotiation.application.NegotiationTasks;
import demo.pipeline.application.PipelineTasks;
import demo.publishing.application.PublishingTasks;
import demo.research.application.ResearchTasks;
import demo.support.application.SupportTasks;
import java.time.Instant;
import java.util.List;

/**
 * Synchronous read + control endpoints for runs. Phase 2 ships only GET /api/samples (the landing
 * list). GET /api/runs/{runId}/status and POST /api/runs/{runId}/stop are added in US1 / US2.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/playground/api")
public class RunControlEndpoint extends AbstractHttpEndpoint {

  public record SampleSummary(String id, String displayName, String agentComponentId) {}

  public record SampleList(List<SampleSummary> samples) {}

  /**
   * Synthesised run status for the UI. {@code runState} is a roll-up of the primary task's status
   * combined with the agent's phase. {@code AWAITING_INPUT} is detected client-side by per-sample
   * descriptors that know each sample's full task graph (currently only publishing).
   */
  public record StopResponse(String runState, Instant stoppedAt) {}

  public record RunStatus(
    String runId,
    String sampleId,
    String agentComponentId,
    String taskId,
    String agentPhase,
    boolean agentPaused,
    String taskStatus,
    String runState,
    Object finalResult,
    String failureReason
  ) {}

  private final ComponentClient componentClient;

  public RunControlEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/samples")
  public SampleList samples() {
    var entries = SampleRegistry.samples()
      .stream()
      .map(e -> new SampleSummary(e.id(), e.displayName(), e.agentComponentId()))
      .toList();
    return new SampleList(entries);
  }

  @Get("/runs/{runId}/status")
  public RunStatus status(String runId) {
    var component = requiredQueryParam("component");
    var task = requiredQueryParam("task");
    var sample = requiredQueryParam("sample");

    var agentClass = SampleRegistry.classFor(component);
    var taskDef = taskDefFor(sample);

    var agentState = componentClient.forAutonomousAgent(agentClass, runId).getState();
    var snapshot = componentClient.forTask(task).get(taskDef);

    var taskStatus = snapshot.status();
    var runState = computeRunState(agentState.phase(), taskStatus);

    return new RunStatus(
      runId,
      sample,
      component,
      task,
      agentState.phase(),
      agentState.suspended(),
      taskStatus.name(),
      runState,
      snapshot.result().orElse(null),
      snapshot.failureReason().orElse(null)
    );
  }

  /**
   * Operator-driven stop. Idempotent — a second call on an already-stopped agent simply returns
   * the same shape. The actual state transition is observed via the Stopped notification on the
   * SSE stream; this endpoint just kicks the SDK and returns immediately.
   */
  @Post("/runs/{runId}/stop")
  public StopResponse stop(String runId) {
    var component = requiredQueryParam("component");
    var agentClass = SampleRegistry.classFor(component);
    componentClient.forAutonomousAgent(agentClass, runId).terminate();
    return new StopResponse("CANCELLED", Instant.now());
  }

  private String requiredQueryParam(String name) {
    var value = requestContext().queryParams().getString(name);
    if (value.isEmpty()) {
      throw HttpException.error(
        StatusCodes.BAD_REQUEST,
        "Missing required query parameter: " + name
      );
    }
    return value.get();
  }

  private static Task<?> taskDefFor(String sampleId) {
    return switch (sampleId) {
      case "helloworld" -> QuestionTasks.ANSWER;
      case "pipeline" -> PipelineTasks.REPORT;
      case "docreview" -> demo.docreview.application.ReviewTasks.REVIEW;
      case "dynamic" -> DynamicTasks.SUMMARIZE; // SUMMARIZE/TRANSLATE both return String
      case "research" -> ResearchTasks.BRIEF;
      case "consulting" -> ConsultingTasks.ENGAGEMENT;
      case "support" -> SupportTasks.RESOLVE;
      case "publishing" -> PublishingTasks.PUBLISH;
      case "debate" -> DebateTasks.DEBATE;
      case "negotiation" -> NegotiationTasks.NEGOTIATE;
      case "peerreview" -> demo.peerreview.application.ReviewTasks.REVIEW;
      case "devteam" -> ProjectTasks.PLAN;
      default -> throw HttpException.error(
        StatusCodes.NOT_FOUND,
        "Unknown sample id: " + sampleId
      );
    };
  }

  private static String computeRunState(String agentPhase, TaskStatus taskStatus) {
    // Driven by the task's terminal state. The agent phase is intentionally NOT consulted: in
    // multi-agent pipelines (e.g. publishing) the owning agent legitimately stops after its
    // share of the work even though the run continues on another agent. Operator-stop is
    // surfaced via the Stopped(reason="operator") notification on the SSE stream, which the
    // client treats as a hard CANCELLED locally; the SDK does not expose the stop reason via
    // getState(), so we cannot distinguish operator-stop from auto-stop server-side here.
    if (taskStatus == TaskStatus.COMPLETED) return "COMPLETED";
    if (taskStatus == TaskStatus.FAILED) return "FAILED";
    if (taskStatus == TaskStatus.CANCELLED) return "CANCELLED";
    if (taskStatus == TaskStatus.PENDING) return "PENDING";
    // ASSIGNED, IN_PROGRESS, RESULT_REJECTED — work continues.
    return "RUNNING";
  }
}
