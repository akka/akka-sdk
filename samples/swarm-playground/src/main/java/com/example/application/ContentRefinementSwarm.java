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
 * Content refinement swarm that coordinates a writer and critic in an
 * iterative revision loop until the content is approved.
 */
@Component(id = "content-refinement-swarm")
public class ContentRefinementSwarm extends Workflow<SwarmState> {

  private static final Logger logger = LoggerFactory.getLogger(ContentRefinementSwarm.class);

  private final ComponentClient componentClient;

  public ContentRefinementSwarm(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  protected String instructions() {
    return """
        You coordinate a content creation and refinement process.

        Follow this loop:
        1. Hand off to the writer agent with the user's brief
        2. Hand off to the critic agent with the writer's output
        3. If the critic responds with APPROVED, you are done — compile the final result
        4. If the critic has feedback, hand off to the writer again with both \
           the current content and the critic's feedback
        5. Repeat steps 2-4

        Track each revision cycle. When compiling the final result, include:
        - The final approved content
        - A summary of each revision (what feedback was given, what changed)
        """;
  }

  protected List<Handoff> handoffs() {
    return List.of(
        Handoff.toAgent(WriterAgent.class)
            .withDescription("Writes or revises content based on a brief and optional feedback from a critic"),
        Handoff.toAgent(CriticAgent.class)
            .withDescription("Reviews content for quality, clarity, accuracy, and tone. Returns feedback or APPROVED")
    );
  }

  protected Class<String> resultType() {
    return String.class;
  }

  // ========== SDK implementation details ==========

  protected int maxTurns() {
    return 20;
  }

  protected int stepTimeoutSeconds() {
    return 5 * 60;
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

  @StepName("orchestrate")
  private StepEffect orchestrateStep() {
    var state = currentState();
    logger.info("Starting content refinement for: {}", state.userMessage());

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
    logger.error("Content refinement failed for: {}", currentState().userMessage());
    return stepEffects()
        .updateState(currentState().failed("Orchestration failed after retries"))
        .thenEnd();
  }

  private String sessionId() {
    return commandContext().workflowId();
  }
}
