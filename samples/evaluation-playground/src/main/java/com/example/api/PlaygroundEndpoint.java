package com.example.api;

import akka.javasdk.agent.evaluator.HallucinationEvaluator;
import akka.javasdk.agent.evaluator.SummarizationEvaluator;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import com.example.application.PromptAgent;

import java.util.UUID;

/**
 * This is a simple Akka Endpoint that uses an agent and LLM to generate
 * greetings in different languages.
 */
// Opened up for access from the public internet to make the service easy to try out.
// For actual services meant for production this must be carefully considered,
// and often set more limited
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class PlaygroundEndpoint {

  private final ComponentClient componentClient;

  public PlaygroundEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/eval/summarization")
  public SummarizationEvaluator.Result evalSummarization(SummarizationEvaluator.EvaluationRequest request) {
    return componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(SummarizationEvaluator::evaluate)
      .invoke(request);
  }

  @Post("/eval/hallucination")
  public HallucinationEvaluator.Result evalHallucination(HallucinationEvaluator.EvaluationRequest request) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(HallucinationEvaluator::evaluate)
        .invoke(request);
  }

  @Post("/eval/toxicity")
  public ToxicityEvaluator.Result evalToxicity(String text) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(ToxicityEvaluator::evaluate)
        .invoke(text);
  }

  @Post("/prompt/{sessionId}")
  public String prompt(String sessionId, PromptAgent.Request request) {
    return componentClient
        .forAgent()
        .inSession(UUID.randomUUID().toString())
        .method(PromptAgent::send)
        .invoke(request);
  }
}

