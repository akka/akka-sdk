/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package demo.docs;

import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.agent.task.TaskStatus;
import akka.javasdk.client.ComponentClient;
import akka.stream.Materializer;
import demo.devteam.application.DeveloperTasks;
import demo.docreview.application.ReviewResult;
import demo.docreview.application.ReviewTasks;
import demo.helloworld.application.QuestionAnswerer;
import demo.pipeline.application.ReportAgent;
import demo.research.application.ResearchBrief;
import demo.research.application.ResearchTasks;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Code snippets for the autonomous-agent documentation pages. Each method body is included in the
 * docs by tag. The class is never executed; it is compiled so the examples stay in step with the
 * SDK and the sample code they reference.
 */
class ClientApiSample {

  private static final Logger log = LoggerFactory.getLogger(ClientApiSample.class);

  private final ComponentClient componentClient;
  private final Materializer materializer;

  ClientApiSample(ComponentClient componentClient, Materializer materializer) {
    this.componentClient = componentClient;
    this.materializer = materializer;
  }

  void terminate(String agentInstanceId) {
    // tag::terminate[]
    componentClient.forAutonomousAgent(ReportAgent.class, agentInstanceId).terminate();
    // end::terminate[]
  }

  void suspendAndResume(String agentInstanceId) {
    // tag::suspend-resume[]
    // Suspend the agent
    componentClient.forAutonomousAgent(ReportAgent.class, agentInstanceId).suspend();

    // Resume the agent
    componentClient.forAutonomousAgent(ReportAgent.class, agentInstanceId).resume();
    // end::suspend-resume[]
  }

  void agentState(String agentInstanceId) {
    // tag::get-state[]
    var state = componentClient
      .forAutonomousAgent(ReportAgent.class, agentInstanceId)
      .getState();

    state.phase(); // "idle", "advance", "model", "tools", or "stopped"
    state.suspended(); // whether the agent is suspended
    state.instructions(); // the agent's current instructions
    state.totalTokenUsage(); // cumulative token usage (inputTokens, outputTokens)
    state.currentTask(); // Optional<TaskKey> with the task currently being worked on
    state.pendingTaskIds(); // List<String> with ids of tasks queued but not yet started
    // end::get-state[]
  }

  void querySnapshot(String taskId) {
    // tag::get-snapshot[]
    var snapshot = componentClient.forTask(taskId).get(ResearchTasks.BRIEF);
    if (snapshot.status() == TaskStatus.COMPLETED) {
      ResearchBrief brief = snapshot.result().orElseThrow();
      log.info("{}: {}", brief.title(), brief.keyFindings());
    }
    // end::get-snapshot[]
  }

  void awaitResult(String taskId) {
    // tag::result[]
    ResearchBrief brief = componentClient.forTask(taskId).result(ResearchTasks.BRIEF);
    // end::result[]
    log.info("Research brief: {}", brief.title());
  }

  void subscribeNotifications(String agentInstanceId) {
    // tag::notification-stream[]
    componentClient
      .forAutonomousAgent(QuestionAnswerer.class, agentInstanceId)
      .notificationStream()
      .runForeach(System.out::println, materializer);
    // end::notification-stream[]
  }

  void notificationFamilies(String agentInstanceId) {
    var agentClient = componentClient.forAutonomousAgent(
      QuestionAnswerer.class,
      agentInstanceId
    );
    // tag::notification-families[]
    agentClient
      .notificationStream()
      .runForeach(
        n -> {
          switch (n) {
            case Notification.LifecycleNotification lifecycle -> renderLifecycle(lifecycle);
            case Notification.TaskNotification task -> renderTask(task);
            case Notification.TeamNotification team -> renderTeam(team);
            default -> {} // ignore
          }
        },
        materializer
      );
    // end::notification-families[]
  }

  void asyncCalls(String agentInstanceId) {
    // tag::async[]
    var stateF = componentClient
      .forAutonomousAgent(ReportAgent.class, agentInstanceId)
      .getStateAsync();

    stateF.thenAccept(state -> log.info("Phase: {}", state.phase()));
    // end::async[]
  }

  void resolveTemplate() {
    // tag::template-params[]
    var task = DeveloperTasks.IMPLEMENT.params(
      Map.of(
        "feature",
        "rate limiter",
        "requirements",
        "10 requests per second per user, with 1-minute window"
      )
    );
    // end::template-params[]
    log.info("Resolved task: {}", task);
  }

  void snapshotFields(String taskId) {
    // tag::snapshot[]
    var snapshot = componentClient.forTask(taskId).get(ReviewTasks.REVIEW);
    // PENDING, ASSIGNED, IN_PROGRESS, RESULT_REJECTED, COMPLETED, FAILED, or CANCELLED
    TaskStatus status = snapshot.status();
    // present if completed
    Optional<ReviewResult> result = snapshot.result();
    // present if failed
    Optional<String> reason = snapshot.failureReason();
    // end::snapshot[]
    log.info("{} {} {}", status, result, reason);
  }

  private void renderLifecycle(Notification.LifecycleNotification notification) {}

  private void renderTask(Notification.TaskNotification notification) {}

  private void renderTeam(Notification.TeamNotification notification) {}
}
