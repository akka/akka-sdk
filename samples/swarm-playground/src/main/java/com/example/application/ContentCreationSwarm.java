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
 * Content creation swarm with a full pipeline: research → write → edit → evaluate,
 * with iterative revision cycles.
 */
@Component(id = "content-creation-swarm")
public class ContentCreationSwarm extends Workflow<SwarmState> {

  private static final Logger logger = LoggerFactory.getLogger(ContentCreationSwarm.class);

  private final ComponentClient componentClient;

  public ContentCreationSwarm(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  protected String instructions() {
    return """
        You are a content production orchestrator. Your goal is to produce a high-quality
        article on the user's topic in their requested writing style.

        Follow this process:

        PHASE 1 — Research
        Break the topic into 3-5 specific sub-topics that need research.
        For each sub-topic, hand off to the researcher agent. The researcher has web search
        and web fetch tools — it will gather deep, factual information from real sources.

        PHASE 2 — Writing
        Once you have sufficient research, hand off to the writer agent with:
        - The original topic
        - All accumulated research findings
        - The requested writing style

        PHASE 3 — Editing
        Hand off the draft to the editor agent for polishing (grammar, flow, readability).

        PHASE 4 — Evaluation
        Hand off the edited content to the evaluator agent, which will assess whether the
        content sufficiently covers the topic. The evaluator responds with APPROVED if the
        content is good, or a numbered list of specific feedback if improvements are needed.

        If the evaluator does not respond with APPROVED and you haven't exceeded 3 revision cycles:
        - Analyze the feedback to identify what's missing
        - Hand off to the researcher for the specific gaps
        - Then back to writer → editor → evaluator
        - Repeat until approved or 3 cycles reached

        PHASE 5 — Final output
        When the evaluator approves (or max cycles reached), compile and return the final
        article as your response.
        """;
  }

  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(ResearcherAgent.class)
            .withDescription("Researches topics using web search and web fetch tools. Returns detailed research findings."),
        Handoff.toAgent(WriterAgent.class)
            .withDescription("Writes or revises content based on a brief, research material, and optional feedback"),
        Handoff.toAgent(EditorAgent.class)
            .withDescription("Polishes drafts for grammar, flow, readability, and professional tone"),
        Handoff.toAgent(EvaluatorAgent.class)
            .withDescription("Evaluates content coverage. Returns APPROVED if good, or numbered feedback list if improvements needed")
    );
  }

  protected Class<String> resultType() {
    return String.class;
  }

  // ========== SDK implementation details ==========

  protected int maxTurns() {
    return 30;
  }

  protected int stepTimeoutSeconds() {
    return 10 * 60;
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
    logger.info("Starting content creation for: {}", state.userMessage());

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
            Swarm completed, final result:
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
    logger.error("Content creation failed for: {}", currentState().userMessage());
    return stepEffects()
        .updateState(currentState().failed("Orchestration failed after retries"))
        .thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
