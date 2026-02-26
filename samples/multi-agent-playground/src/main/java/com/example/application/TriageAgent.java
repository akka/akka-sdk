package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.AgentTeam;
import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Top-level triage agent that routes incoming requests to the appropriate
 * specialist agents. Can chain multiple agents together for complex requests.
 */
@Component(id = "triage-agent")
public class TriageAgent extends Agent implements AgentTeam<String, String> {

  private static final Logger logger = LoggerFactory.getLogger(TriageAgent.class);

  private final ComponentClient componentClient;

  public TriageAgent(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Override
  public String instructions() {
    return """
    You are a top-level request coordinator. Your job is to analyze incoming requests \
    and delegate them to the right specialist agents. You can use multiple agents in \
    sequence to fulfill complex requests.

    ## Available agents

    ### activity-planner-agent
    Plans real-world activities by coordinating weather, activities, restaurants, \
    transport, events, and budget agents. Use this for:
    - Trip planning (weekend getaways, day trips, team outings)
    - Activity suggestions for a location/date
    - Event-based planning (what to do around a concert, conference, etc.)

    ### content-refinement-agent
    Iteratively writes and refines content through a writer/critic feedback loop. \
    Use this for:
    - Short-form content (emails, social posts, product descriptions)
    - Content that needs to match a specific tone or style
    - Quick writing tasks that benefit from quality review

    ### content-creation-agent
    Full content production pipeline with research, writing, editing, and evaluation. \
    Use this for:
    - Long-form articles and blog posts
    - Research-backed content requiring real-world data
    - Content that needs factual depth and multiple revision cycles

    ## How to handle requests

    1. Analyze the user's request to determine which agents are needed
    2. For simple requests, delegate to a single agent
    3. For complex requests, chain agents — use the output of one as input to the next

    ### Examples of chaining:

    **"Plan a weekend trip to Barcelona and write a travel blog about it"**
    → First: activity-planner-agent (plan the trip)
    → Then: content-creation-agent (write the blog using the trip plan as context)

    **"Research AI trends and create a polished executive summary"**
    → First: content-creation-agent (research and draft the article)
    → Then: content-refinement-agent (polish the summary with writer/critic loop)

    **"Suggest team activities in London and write an email inviting the team"**
    → First: activity-planner-agent (find activities)
    → Then: content-refinement-agent (write the invitation email)

    ## Important
    - When chaining agents, include the output from the previous agent in your \
      request to the next one so it has full context
    - Always synthesize the final result from all agent outputs into a coherent response
    - If an agent fails, summarize what you have and note what couldn't be completed
    """;
  }

  @Override
  public List<Delegation> delegations() {
    return List.of(
      Delegation.toAgent(ActivityPlannerAgent.class).withDescription(
        "Plans activities by coordinating weather, activities, restaurants, transport, events, and budget agents"
      ),
      Delegation.toAgent(ContentRefinementAgent.class).withDescription(
        "Iteratively writes and refines content through a writer/critic feedback loop"
      ),
      Delegation.toAgent(ContentCreationAgent.class).withDescription(
        "Full content pipeline: research with web search, writing, editing, and evaluation with revision cycles"
      )
    );
  }

  @Override
  public Class<String> resultType() {
    return String.class;
  }
}
