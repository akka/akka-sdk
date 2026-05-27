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
import java.util.ArrayList;
import java.util.List;
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
   * {@code agentComponentId} / {@code agentInstanceId} identify the agent whose stream emitted
   * this event — set by the merger so child-agent events can be attributed correctly in the UI.
   */
  public record EventEnvelope(
    long eventId,
    Instant timestamp,
    String tier,
    String category,
    String kind,
    /** Serialised body of the notification — JSON-encoded by Jackson; clients only read fields
     *  they recognise. */
    Notification raw,
    String agentComponentId,
    String agentInstanceId
  ) {}

  private final ComponentClient componentClient;

  public RunNotificationEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  /** Maximum number of concurrent inner sources flatMapMerge will run. Must be > the deepest
   *  fan-out we expect; for our samples that's well under a dozen. */
  private static final int MERGE_PARALLELISM = 32;

  /** A reference to a (potentially child) agent extracted from a parent's notification. */
  private record AgentRef(String componentId, String instanceId) {}

  /** A notification carried alongside the (componentId, instanceId) of the agent whose stream
   *  emitted it. This is the merger's internal currency — the EventEnvelope wire shape is
   *  produced from it at the very end. */
  private record TaggedNotification(AgentRef emitter, Notification notification) {}

  @Get("/runs/{runId}/events")
  public HttpResponse events(String runId) {
    var component = requestContext()
      .queryParams()
      .getString("component")
      .orElseThrow(
        () ->
          akka.javasdk.http.HttpException.error(
            akka.http.javadsl.model.StatusCodes.BAD_REQUEST,
            "Missing required query parameter: component"
          )
      );

    var agentClass = SampleRegistry.classFor(component);
    var counter = new AtomicLong(0);
    var rootAgent = new AgentRef(component, runId);

    // Recursively splice in child agent streams: when an agent emits a spawn notification
    // (DelegationStarted / TeamCreated / HandoffStarted / ConversationCreated) we extract the
    // child component+instance ids and merge each child's notificationStream into the same
    // outgoing stream. Each notification is tagged with the emitter's (componentId, instanceId)
    // so the UI can attribute it correctly. The expansion is recursive — children's own spawn
    // events also expand — so multi-level nesting is supported.
    Source<TaggedNotification, NotUsed> mergedSource = taggedStream(
      rootAgent,
      agentClass
    ).flatMapMerge(MERGE_PARALLELISM, this::expand);

    Source<EventEnvelope, NotUsed> envelopes = mergedSource.map(
      t ->
        new EventEnvelope(
          counter.incrementAndGet(),
          Instant.now(),
          tierFor(t.notification()),
          categoryFor(t.notification()),
          t.notification().getClass().getSimpleName(),
          t.notification(),
          t.emitter().componentId(),
          t.emitter().instanceId()
        )
    );

    return HttpResponses.serverSentEvents(envelopes, env -> String.valueOf(env.eventId()));
  }

  /** Subscribe to one agent's notificationStream and tag every emission with that agent's id. */
  private Source<TaggedNotification, NotUsed> taggedStream(
    AgentRef agent,
    Class<? extends akka.javasdk.agent.autonomous.AutonomousAgent> clazz
  ) {
    return componentClient
      .forAutonomousAgent(clazz, agent.instanceId())
      .notificationStream()
      .map(n -> new TaggedNotification(agent, n));
  }

  /**
   * For a single tagged notification, return a Source that emits the notification itself plus
   * any child-agent notifications discovered from it. Calls itself recursively on each child
   * stream so deeply-nested coordination patterns are flattened into a single merged stream
   * with correct emitter attribution preserved end to end.
   */
  private Source<TaggedNotification, NotUsed> expand(TaggedNotification tagged) {
    var children = childAgentsOf(tagged.notification());
    if (children.isEmpty()) return Source.single(tagged);

    Source<TaggedNotification, NotUsed> childMerged = Source.from(children)
      // Filter out unknown component ids (e.g. request-based Agents that have no
      // notificationStream, or future child agents not yet registered) — we just skip them.
      .filter(c -> SampleRegistry.classForOrNull(c.componentId()) != null)
      .flatMapMerge(MERGE_PARALLELISM, c -> {
        var clazz = SampleRegistry.classForOrNull(c.componentId());
        return taggedStream(c, clazz).flatMapMerge(MERGE_PARALLELISM, this::expand); // recurse for grandchildren
      });

    return Source.single(tagged).concat(childMerged);
  }

  /**
   * Extract the (componentId, instanceId) pairs of child agents named in a spawn notification.
   * Returns an empty list for notifications that aren't spawn events.
   */
  private static List<AgentRef> childAgentsOf(Notification n) {
    if (n instanceof Notification.DelegationStarted ds) {
      return zip(ds.workerComponentIds(), ds.workerInstanceIds());
    }
    if (n instanceof Notification.TeamCreated tc) {
      return zip(tc.memberComponentIds(), tc.memberInstanceIds());
    }
    if (n instanceof Notification.HandoffStarted hs) {
      return List.of(new AgentRef(hs.targetComponentId(), hs.targetInstanceId()));
    }
    if (n instanceof Notification.ConversationCreated cc) {
      return zip(cc.participantComponentIds(), cc.participantInstanceIds());
    }
    return List.of();
  }

  private static List<AgentRef> zip(List<String> componentIds, List<String> instanceIds) {
    var size = Math.min(componentIds.size(), instanceIds.size());
    var refs = new ArrayList<AgentRef>(size);
    for (int i = 0; i < size; i++) {
      refs.add(new AgentRef(componentIds.get(i), instanceIds.get(i)));
    }
    return refs;
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
