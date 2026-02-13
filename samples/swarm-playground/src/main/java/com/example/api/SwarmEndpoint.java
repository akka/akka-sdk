package com.example.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import akka.javasdk.swarm.SwarmResult;
import com.example.application.ActivityPlannerSwarm;
import com.example.application.ContentCreationSwarm;
import com.example.application.ContentRefinementSwarm;

import java.util.Optional;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/swarm")
public class SwarmEndpoint {

  public record StartRequest(String message) {}
  public record StatusResponse(String status, Optional<String> result) {}

  private final ComponentClient componentClient;

  public SwarmEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/activity-planner/{swarmId}")
  public HttpResponse start(String swarmId, StartRequest request) {
    componentClient.forWorkflow(swarmId)
        .method(ActivityPlannerSwarm::run)
        .invoke(request.message());
    return HttpResponses.created(swarmId, "/swarm/activity-planner/" + swarmId);
  }

  @Get("/activity-planner/{swarmId}")
  public StatusResponse getResult(String swarmId) {
    return toStatusResponse(componentClient.forWorkflow(swarmId)
        .method(ActivityPlannerSwarm::getResult)
        .invoke());
  }

  @Post("/content-refinement/{swarmId}")
  public HttpResponse startContentRefinement(String swarmId, StartRequest request) {
    componentClient.forWorkflow(swarmId)
        .method(ContentRefinementSwarm::run)
        .invoke(request.message());
    return HttpResponses.created(swarmId, "/swarm/content-refinement/" + swarmId);
  }

  @Get("/content-refinement/{swarmId}")
  public StatusResponse getContentRefinementResult(String swarmId) {
    return toStatusResponse(componentClient.forWorkflow(swarmId)
        .method(ContentRefinementSwarm::getResult)
        .invoke());
  }

  @Post("/content-creation/{swarmId}")
  public HttpResponse startContentCreation(String swarmId, StartRequest request) {
    componentClient.forWorkflow(swarmId)
        .method(ContentCreationSwarm::run)
        .invoke(request.message());
    return HttpResponses.created(swarmId, "/swarm/content-creation/" + swarmId);
  }

  @Get("/content-creation/{swarmId}")
  public StatusResponse getContentCreationResult(String swarmId) {
    return toStatusResponse(componentClient.forWorkflow(swarmId)
        .method(ContentCreationSwarm::getResult)
        .invoke());
  }

  private StatusResponse toStatusResponse(SwarmResult<String> swarmResult) {
    return switch (swarmResult) {
      case SwarmResult.Running<?> r -> new StatusResponse("RUNNING", Optional.empty());
      case SwarmResult.Completed<String> c -> new StatusResponse("COMPLETED", Optional.of(c.result()));
      case SwarmResult.Failed<?> f -> new StatusResponse("FAILED", Optional.of(f.reason()));
      case SwarmResult.Paused<?> p -> new StatusResponse("PAUSED", Optional.empty());
      case SwarmResult.Stopped<?> s -> new StatusResponse("STOPPED", Optional.of(s.reason()));
    };
  }
}
