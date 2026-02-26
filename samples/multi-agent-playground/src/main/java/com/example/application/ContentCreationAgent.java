package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content creation agent with a full pipeline: research → write → edit → evaluate,
 * with iterative revision cycles.
 */
@Component(id = "content-creation-agent")
public class ContentCreationAgent extends Agent implements AgentTeam<String, String> {

  private static final Logger logger = LoggerFactory.getLogger(ContentCreationAgent.class);

  private final ComponentClient componentClient;

  public ContentCreationAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public String instructions() {
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

  @Override
  public List<Delegation> delegations() {
    return List.of(
      Delegation.toAgent(ResearcherAgent.class).withDescription(
        "Researches topics using web search and web fetch tools. Returns detailed research findings."
      ),
      Delegation.toAgent(WriterAgent.class).withDescription(
        "Writes or revises content based on a brief, research material, and optional feedback"
      ),
      Delegation.toAgent(EditorAgent.class).withDescription(
        "Polishes drafts for grammar, flow, readability, and professional tone"
      ),
      Delegation.toAgent(EvaluatorAgent.class).withDescription(
        "Evaluates content coverage. Returns APPROVED if good, or numbered feedback list if improvements needed"
      )
    );
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
