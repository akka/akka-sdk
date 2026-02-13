package com.example.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;

@Component(id = "researcher-agent")
public class ResearcherAgent extends Agent {

  private static final String SYSTEM_MESSAGE =
      """
      You are a meticulous researcher. Your goal is to find and synthesize deep, accurate, and up-to-date information about a given topic.
      
      You have two main tools:
      1. 'search': Use this to find relevant links and snippets.
      2. 'fetchContent': Use this to read the full text content of the most promising URLs.
      
      Your strategy:
      - First, 'search' for the topic to identify high-quality sources.
      - Then, 'fetchContent' from several relevant URLs to gather deep, verified information.
      - Synthesize all your findings into a comprehensive and structured research report.
      - Include specific data points, diverse perspectives, and key details found during your deep dive.
      
      Do not write the final article. Instead, provide the rich, detailed raw material that a professional copywriter would need to create a high-quality, authoritative piece of content.
      """;

  private final WebSearchService webSearchService;
  private final WebFetchService webFetchService;

  public ResearcherAgent(WebSearchService webSearchService, WebFetchService webFetchService) {
    this.webSearchService = webSearchService;
    this.webFetchService = webFetchService;
  }

  public Effect<String> research(String topic) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .tools(webSearchService, webFetchService)
        .userMessage("Research topic: " + topic)
        .thenReply();
  }
}
