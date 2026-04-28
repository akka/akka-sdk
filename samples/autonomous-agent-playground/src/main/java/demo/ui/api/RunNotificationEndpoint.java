package demo.ui.api;

import akka.NotUsed;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.agent.autonomous.Notification;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bridges per-run autonomous-agent notification streams to the browser as Server-Sent Events.
 * Each Notification is wrapped in a small {@link EventEnvelope} carrying a stable wire shape
 * (tier, category, kind) so the UI doesn't need to know every SDK Notification subtype.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/playground/api")
public class RunNotificationEndpoint extends AbstractHttpEndpoint {

  /**
   * Stable wire shape for a single runtime notification. {@code tier} drives the three-tier visual
   * differentiation in the UI (healthy / struggle / terminal_failure). {@code category} groups by
   * notification family. {@code kind} is the simple class name. {@code raw} is the SDK record
   * itself, JSON-serialised by Jackson — clients only read fields they recognise.
   */
  public record EventEnvelope(
    long eventId,
    Instant timestamp,
    String tier,
    String category,
    String kind,
    /**
     * Serialised body of the notification. Typed as Object because empty-marker notifications
     * (e.g. {@code Activated}) have no fields and would trip Jackson's
     * {@code FAIL_ON_EMPTY_BEANS}; for those we emit an empty map instead.
     */
    Object raw
  ) {}

  private final ComponentClient componentClient;

  public RunNotificationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/runs/{runId}/events")
  public HttpResponse events(String runId) {
    var component = requestContext().queryParams().getString("component")
      .orElseThrow(() ->
        akka.javasdk.http.HttpException.error(
          akka.http.javadsl.model.StatusCodes.BAD_REQUEST,
          "Missing required query parameter: component"
        )
      );

    var agentClass = AgentRegistry.classFor(component);
    var counter = new AtomicLong(0);

    Source<Notification, NotUsed> source = componentClient
      .forAutonomousAgent(agentClass, runId)
      .notificationStream();

    Source<EventEnvelope, NotUsed> envelopes = source.map(n ->
      new EventEnvelope(
        counter.incrementAndGet(),
        Instant.now(),
        tierFor(n),
        categoryFor(n),
        n.getClass().getSimpleName(),
        rawFor(n)
      )
    );

    return HttpResponses.serverSentEvents(envelopes, env -> String.valueOf(env.eventId()));
  }

  /**
   * Pick the serialisation body for a notification. Empty-marker notifications (Activated,
   * Deactivated, IterationStarted) have no fields and would fail Jackson's bean-detection;
   * for those we substitute an empty map so the SSE frame still renders cleanly.
   */
  static Object rawFor(Notification n) {
    if (n instanceof Notification.Activated
        || n instanceof Notification.Deactivated
        || n instanceof Notification.IterationStarted) {
      return Map.of();
    }
    return n;
  }

  /**
   * Three-tier classification per FR-011 + Q3 clarification (struggle vs terminal failure).
   */
  public static String tierFor(Notification n) {
    if (n instanceof Notification.StruggleNotification) return "struggle";
    if (n instanceof Notification.IterationFailed) return "struggle";
    if (n instanceof Notification.TaskResultRejected) return "struggle";
    if (n instanceof Notification.TaskDependencyWait) return "struggle";
    if (n instanceof Notification.TeamMemberSetupFailed) return "terminal_failure";
    if (n instanceof Notification.TaskFailed) return "terminal_failure";
    if (n instanceof Notification.TaskCancelled) return "terminal_failure";
    if (n instanceof Notification.Stopped s && "operator".equals(s.reason())) {
      return "terminal_failure";
    }
    return "healthy";
  }

  /**
   * Category derived from the marker sub-interface so the UI can group rows by family.
   */
  public static String categoryFor(Notification n) {
    if (n instanceof Notification.LifecycleNotification) return "lifecycle";
    if (n instanceof Notification.TaskNotification) return "task";
    if (n instanceof Notification.HandoffNotification) return "handoff";
    if (n instanceof Notification.DelegationNotification) return "delegation";
    if (n instanceof Notification.TeamNotification) return "team";
    if (n instanceof Notification.BacklogNotification) return "backlog";
    if (n instanceof Notification.ConversationNotification) return "conversation";
    if (n instanceof Notification.MessagingNotification) return "messaging";
    if (n instanceof Notification.StruggleNotification) return "struggle";
    return "other";
  }
}
