package demo.ui.api;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.agent.autonomous.AutonomousAgent;
import akka.javasdk.http.HttpException;
import demo.consulting.application.ConsultingCoordinator;
import demo.consulting.application.ConsultingResearcher;
import demo.consulting.application.SeniorConsultant;
import demo.debate.application.Advocate;
import demo.debate.application.Critic;
import demo.debate.application.DebateModerator;
import demo.devteam.application.Developer;
import demo.devteam.application.ProjectLead;
import demo.docreview.application.DocumentReviewer;
import demo.dynamic.application.DynamicAgent;
import demo.helloworld.application.QuestionAnswerer;
import demo.negotiation.application.Buyer;
import demo.negotiation.application.Facilitator;
import demo.negotiation.application.Seller;
import demo.peerreview.application.ComplianceReviewer;
import demo.peerreview.application.ReviewModerator;
import demo.peerreview.application.StyleReviewer;
import demo.peerreview.application.TechnicalReviewer;
import demo.pipeline.application.ReportAgent;
import demo.publishing.application.ContentAgent;
import demo.publishing.application.PublishingAgent;
import demo.research.application.Analyst;
import demo.research.application.ResearchCoordinator;
import demo.research.application.Researcher;
import demo.support.application.BillingSpecialist;
import demo.support.application.TechnicalSpecialist;
import demo.support.application.TriageAgent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps an agent's @Component(id = ...) string to its concrete AutonomousAgent class.
 *
 * <p>Two views over the same data:
 * <ul>
 *   <li>{@link #samples()} — the landing-page list, one entry per <em>owning</em> agent (the
 *       agent each sample's POST endpoint dispatches to first).</li>
 *   <li>{@link #classFor(String)} — universal lookup that also resolves <em>child</em> agents
 *       (Researcher, Analyst, Advocate, Critic, BillingSpecialist, etc.). The notification-stream
 *       endpoint uses this to subscribe to children dynamically when the parent emits a
 *       DelegationStarted / TeamCreated / HandoffStarted / ConversationCreated event.</li>
 * </ul>
 */
public final class SampleRegistry {

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
    new SampleEntry(
      "docreview",
      "Document review",
      "document-reviewer",
      DocumentReviewer.class
    ),
    new SampleEntry("dynamic", "Dynamic", "dynamic-agent", DynamicAgent.class),
    new SampleEntry(
      "research",
      "Research",
      "research-coordinator",
      ResearchCoordinator.class
    ),
    new SampleEntry(
      "consulting",
      "Consulting",
      "consulting-coordinator",
      ConsultingCoordinator.class
    ),
    new SampleEntry("support", "Support", "triage-agent", TriageAgent.class),
    new SampleEntry("publishing", "Publishing", "content-agent", ContentAgent.class),
    new SampleEntry("debate", "Debate", "debate-moderator", DebateModerator.class),
    new SampleEntry("negotiation", "Negotiation", "facilitator", Facilitator.class),
    new SampleEntry("peerreview", "Peer review", "review-moderator", ReviewModerator.class),
    new SampleEntry("devteam", "Devteam", "project-lead", ProjectLead.class)
  );

  private static final Map<String, SampleEntry> BY_SAMPLE_ID = ENTRIES.stream()
    .collect(Collectors.toMap(SampleEntry::id, e -> e));

  /** Universal componentId → class lookup. Includes every owning agent <em>and</em> every
   *  child agent reachable from a sample's pipeline. */
  private static final Map<String, Class<? extends AutonomousAgent>> ALL_AGENTS;

  static {
    var map = new HashMap<String, Class<? extends AutonomousAgent>>();
    // Owning agents — every entry in ENTRIES contributes here.
    for (var e : ENTRIES) map.put(e.agentComponentId(), e.agentClass());
    // Child agents reached via delegation / handoff / team / moderation.
    map.put("researcher", Researcher.class); // research → delegation
    map.put("analyst", Analyst.class);
    map.put("consulting-researcher", ConsultingResearcher.class); // consulting → delegation
    map.put("senior-consultant", SeniorConsultant.class); // consulting → handoff
    // FactCheckAgent is intentionally omitted — it's a request-based Agent (not AutonomousAgent)
    // and therefore doesn't have a notificationStream() to subscribe to.
    map.put("billing-specialist", BillingSpecialist.class); // support → handoff
    map.put("technical-specialist", TechnicalSpecialist.class);
    map.put("publishing-agent", PublishingAgent.class); // publishing → publish task
    map.put("advocate", Advocate.class); // debate → moderation
    map.put("critic", Critic.class);
    map.put("buyer", Buyer.class); // negotiation → moderation
    map.put("seller", Seller.class);
    map.put("technical-reviewer", TechnicalReviewer.class); // peerreview → moderation
    map.put("style-reviewer", StyleReviewer.class);
    map.put("compliance-reviewer", ComplianceReviewer.class);
    map.put("developer", Developer.class); // devteam → team
    ALL_AGENTS = Map.copyOf(map);
  }

  private SampleRegistry() {}

  public static List<SampleEntry> samples() {
    return ENTRIES;
  }

  public static Class<? extends AutonomousAgent> classFor(String agentComponentId) {
    var clazz = ALL_AGENTS.get(agentComponentId);
    if (clazz == null) {
      throw HttpException.error(
        StatusCodes.NOT_FOUND,
        "Unknown agent component id: " + agentComponentId
      );
    }
    return clazz;
  }

  /** Best-effort lookup; returns null for unknown ids instead of throwing. Used by the
   *  notification-stream merger when discovering child agents from runtime notifications —
   *  unknown ids are skipped silently. */
  public static Class<? extends AutonomousAgent> classForOrNull(String agentComponentId) {
    return ALL_AGENTS.get(agentComponentId);
  }

  public static SampleEntry forSample(String sampleId) {
    var entry = BY_SAMPLE_ID.get(sampleId);
    if (entry == null) {
      throw HttpException.error(StatusCodes.NOT_FOUND, "Unknown sample id: " + sampleId);
    }
    return entry;
  }
}
