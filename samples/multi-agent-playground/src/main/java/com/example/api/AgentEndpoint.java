package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.example.application.ActivityPlannerAgent;
import com.example.application.ContentCreationAgent;
import com.example.application.ContentRefinementAgent;
import akka.javasdk.agent.AgentTeam;
import com.example.application.LanguageTriageAgent;
import com.example.application.TriageAgent;
import java.util.Optional;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/agent")
public class AgentEndpoint {

  public record StartRequest(String message) {}

  public record StatusResponse(String status, Optional<String> result) {}

  private final ComponentClient componentClient;

  public AgentEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activity-planner/{sessionId}")
  public HttpResponse startActivityPlanner(String sessionId, StartRequest request) {
    componentClient
      .forAgent()
      .inSession(sessionId)
      .method(ActivityPlannerAgent::run)
      .invoke(request.message());
    return HttpResponses.created(sessionId, "/agent/activity-planner/" + sessionId);
  }

  @Get("/activity-planner/{sessionId}")
  public StatusResponse getActivityPlannerResult(String sessionId) {
    return toStatusResponse(
      componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ActivityPlannerAgent::getResult)
        .invoke()
    );
  }

  @Post("/content-refinement/{sessionId}")
  public HttpResponse startContentRefinement(String sessionId, StartRequest request) {
    componentClient
      .forAgent()
      .inSession(sessionId)
      .method(ContentRefinementAgent::run)
      .invoke(request.message());
    return HttpResponses.created(sessionId, "/agent/content-refinement/" + sessionId);
  }

  @Get("/content-refinement/{sessionId}")
  public StatusResponse getContentRefinementResult(String sessionId) {
    return toStatusResponse(
      componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ContentRefinementAgent::getResult)
        .invoke()
    );
  }

  @Post("/content-creation/{sessionId}")
  public HttpResponse startContentCreation(String sessionId, StartRequest request) {
    componentClient
      .forAgent()
      .inSession(sessionId)
      .method(ContentCreationAgent::run)
      .invoke(request.message());
    return HttpResponses.created(sessionId, "/agent/content-creation/" + sessionId);
  }

  @Get("/content-creation/{sessionId}")
  public StatusResponse getContentCreationResult(String sessionId) {
    return toStatusResponse(
      componentClient
        .forAgent()
        .inSession(sessionId)
        .method(ContentCreationAgent::getResult)
        .invoke()
    );
  }

  @Post("/triage/{sessionId}")
  public HttpResponse startTriage(String sessionId, StartRequest request) {
    componentClient
      .forAgent()
      .inSession(sessionId)
      .method(TriageAgent::run)
      .invoke(request.message());
    return HttpResponses.created(sessionId, "/agent/triage/" + sessionId);
  }

  @Get("/triage/{sessionId}")
  public StatusResponse getTriageResult(String sessionId) {
    return toStatusResponse(
      componentClient.forAgent().inSession(sessionId).method(TriageAgent::getResult).invoke()
    );
  }

  @Post("/language-triage/{sessionId}")
  public HttpResponse startLanguageTriage(String sessionId, StartRequest request) {
    componentClient
      .forAgent()
      .inSession(sessionId)
      .method(LanguageTriageAgent::run)
      .invoke(request.message());
    return HttpResponses.created(sessionId, "/agent/language-triage/" + sessionId);
  }

  @Get("/language-triage/{sessionId}")
  public StatusResponse getLanguageTriageResult(String sessionId) {
    return toStatusResponse(
      componentClient.forAgent().inSession(sessionId).method(LanguageTriageAgent::getResult).invoke()
    );
  }

  private StatusResponse toStatusResponse(AgentTeam.Result<String> result) {
    return switch (result) {
      case AgentTeam.Result.Running<String> r -> new StatusResponse("RUNNING", Optional.empty());
      case AgentTeam.Result.Completed<String> c -> new StatusResponse("COMPLETED", Optional.of(c.result()));
      case AgentTeam.Result.Failed<String> f -> new StatusResponse("FAILED", Optional.of(f.reason()));
    };
  }
}
