package demo.ui.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.http.HttpException;
import demo.consulting.application.ConsultingCoordinator;
import demo.debate.application.DebateModerator;
import demo.devteam.application.ProjectLead;
import demo.docreview.application.DocumentReviewer;
import demo.dynamic.application.DynamicAgent;
import demo.helloworld.application.QuestionAnswerer;
import demo.negotiation.application.Facilitator;
import demo.peerreview.application.ReviewModerator;
import demo.pipeline.application.ReportAgent;
import demo.publishing.application.ContentAgent;
import demo.research.application.ResearchCoordinator;
import demo.support.application.TriageAgent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps the owning agent's @Component(id = ...) string to its concrete AutonomousAgent class. Used
 * by the UI's run-status, run-stop, and notification-stream endpoints to resolve the class needed
 * by componentClient.forAutonomousAgent(class, instanceId). Covers the run's owning agent only;
 * delegated/team children are not subscribed to in this MVP (see research R-3).
 */
public final class AgentRegistry {

  public record SampleEntry(
    String id,
    String displayName,
    String agentComponentId,
    Class<? extends AutonomousAgent> agentClass
  ) {}

  // Order matches the README's overview-table ordering.
  private static final List<SampleEntry> ENTRIES = List.of(
    new SampleEntry("helloworld", "Hello world", "question-answerer", QuestionAnswerer.class),
    new SampleEntry("pipeline", "Pipeline", "report-agent", ReportAgent.class),
    new SampleEntry("docreview", "Document review", "document-reviewer", DocumentReviewer.class),
    new SampleEntry("dynamic", "Dynamic", "dynamic-agent", DynamicAgent.class),
    new SampleEntry("research", "Research", "research-coordinator", ResearchCoordinator.class),
    new SampleEntry("consulting", "Consulting", "consulting-coordinator", ConsultingCoordinator.class),
    new SampleEntry("support", "Support", "triage-agent", TriageAgent.class),
    new SampleEntry("publishing", "Publishing", "content-agent", ContentAgent.class),
    new SampleEntry("debate", "Debate", "debate-moderator", DebateModerator.class),
    new SampleEntry("negotiation", "Negotiation", "facilitator", Facilitator.class),
    new SampleEntry("peerreview", "Peer review", "review-moderator", ReviewModerator.class),
    new SampleEntry("devteam", "Devteam", "project-lead", ProjectLead.class)
  );

  private static final Map<String, SampleEntry> BY_COMPONENT_ID = ENTRIES.stream()
    .collect(Collectors.toMap(SampleEntry::agentComponentId, e -> e));

  private static final Map<String, SampleEntry> BY_SAMPLE_ID = ENTRIES.stream()
    .collect(Collectors.toMap(SampleEntry::id, e -> e));

  private AgentRegistry() {}

  public static List<SampleEntry> samples() {
    return ENTRIES;
  }

  public static Class<? extends AutonomousAgent> classFor(String agentComponentId) {
    var entry = BY_COMPONENT_ID.get(agentComponentId);
    if (entry == null) {
      throw HttpException.error(
        StatusCodes.NOT_FOUND,
        "Unknown agent component id: " + agentComponentId
      );
    }
    return entry.agentClass();
  }

  public static SampleEntry forSample(String sampleId) {
    var entry = BY_SAMPLE_ID.get(sampleId);
    if (entry == null) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Unknown sample id: " + sampleId);
    }
    return entry;
  }
}
