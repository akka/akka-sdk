package com.example.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.swarm.Handoff;
import akka.javasdk.swarm.SwarmOrchestratorAgent;
import akka.javasdk.swarm.SwarmResult;
import akka.javasdk.swarm.SwarmState;
import akka.javasdk.workflow.Workflow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.time.Duration.ofSeconds;

/**
 * Top-level triage swarm that routes incoming requests to the appropriate
 * specialist swarms. Can chain multiple swarms together for complex requests.
 */
@Component(id = "triage-swarm")
public class TriageSwarm extends Workflow<SwarmState> {

  private static final Logger logger = LoggerFactory.getLogger(TriageSwarm.class);

  private final ComponentClient componentClient;

  public TriageSwarm(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  protected String instructions() {
    return """
        You are a top-level request coordinator. Your job is to analyze incoming requests \
        and delegate them to the right specialist swarms. You can use multiple swarms in \
        sequence to fulfill complex requests.

        ## Available swarms

        ### activity-planner-swarm
        Plans real-world activities by coordinating weather, activities, restaurants, \
        transport, events, and budget agents. Use this for:
        - Trip planning (weekend getaways, day trips, team outings)
        - Activity suggestions for a location/date
        - Event-based planning (what to do around a concert, conference, etc.)

        ### content-refinement-swarm
        Iteratively writes and refines content through a writer/critic feedback loop. \
        Use this for:
        - Short-form content (emails, social posts, product descriptions)
        - Content that needs to match a specific tone or style
        - Quick writing tasks that benefit from quality review

        ### content-creation-swarm
        Full content production pipeline with research, writing, editing, and evaluation. \
        Use this for:
        - Long-form articles and blog posts
        - Research-backed content requiring real-world data
        - Content that needs factual depth and multiple revision cycles

        ## How to handle requests

        1. Analyze the user's request to determine which swarms are needed
        2. For simple requests, delegate to a single swarm
        3. For complex requests, chain swarms — use the output of one as input to the next

        ### Examples of chaining:

        **"Plan a weekend trip to Barcelona and write a travel blog about it"**
        → First: activity-planner-swarm (plan the trip)
        → Then: content-creation-swarm (write the blog using the trip plan as context)

        **"Research AI trends and create a polished executive summary"**
        → First: content-creation-swarm (research and draft the article)
        → Then: content-refinement-swarm (polish the summary with writer/critic loop)

        **"Suggest team activities in London and write an email inviting the team"**
        → First: activity-planner-swarm (find activities)
        → Then: content-refinement-swarm (write the invitation email)

        ## Important
        - When chaining swarms, include the output from the previous swarm in your \
          request to the next one so it has full context
        - Always synthesize the final result from all swarm outputs into a coherent response
        - If a swarm fails, summarize what you have and note what couldn't be completed
        """;
  }

  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toSwarm(ActivityPlannerSwarm.class)
            .withDescription("Plans activities by coordinating weather, activities, restaurants, transport, events, and budget agents"),
        Handoff.toSwarm(ContentRefinementSwarm.class)
            .withDescription("Iteratively writes and refines content through a writer/critic feedback loop"),
        Handoff.toSwarm(ContentCreationSwarm.class)
            .withDescription("Full content pipeline: research with web search, writing, editing, and evaluation with revision cycles")
    );
  }

  protected Class<String> resultType() {
    return String.class;
  }

  // ========== SDK implementation details ==========

  protected int maxTurns() {
    return 10;
  }

  protected int stepTimeoutSeconds() {
    return 30 * 60; // 30 minutes — sub-swarms can take a while
  }

  protected int maxRetries() {
    return 1;
  }

  @Override
  public WorkflowSettings settings() {
    return WorkflowSettings.builder()
        .defaultStepTimeout(ofSeconds(stepTimeoutSeconds()))
        .defaultStepRecovery(maxRetries(maxRetries()).failoverTo("error"))
        .build();
  }

  public Effect<String> run(String input) {
    if (currentState() != null) {
      return effects().error("Swarm '" + commandContext().workflowId() + "' already started");
    }
    return effects()
        .updateState(SwarmState.initial(input, maxTurns()))
        .transitionTo("orchestrate")
        .thenReply("started");
  }

  public ReadOnlyEffect<SwarmResult<String>> getResult() {
    if (currentState() == null) {
      return effects().error("Swarm not started");
    }
    return effects().reply(currentState().toSwarmResult());
  }

  // ========== Workflow steps ==========

  @StepName("orchestrate")
  private StepEffect orchestrateStep() {
    var state = currentState();
    logger.info("Triage starting for: {}", state.userMessage());

    var request = new SwarmOrchestratorAgent.OrchestrateRequest(
        instructions(),
        handoffs(),
        state.userMessage(),
        sessionId());

    var result = componentClient
        .forAgent()
        .inSession(sessionId())
        .method(SwarmOrchestratorAgent::orchestrate)
        .invoke(request);

    logger.info("""
            Triage swarm completed, final result:
            --------------------------------------------------------------------------------------------
            {}
            --------------------------------------------------------------------------------------------""",
        result);

    return stepEffects()
        .updateState(state.completed(result))
        .thenEnd();
  }

  @StepName("error")
  private StepEffect errorStep() {
    logger.error("Triage failed for: {}", currentState().userMessage());
    return stepEffects()
        .updateState(currentState().failed("Triage orchestration failed after retries"))
        .thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
