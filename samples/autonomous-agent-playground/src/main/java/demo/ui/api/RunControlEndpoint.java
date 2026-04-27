package demo.ui.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import java.util.List;

/**
 * Synchronous read + control endpoints for runs. Phase 2 ships only GET /api/samples (the landing
 * list). GET /api/runs/{runId}/status and POST /api/runs/{runId}/stop are added in US1 / US2.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/playground/api")
public class RunControlEndpoint {

  public record SampleSummary(String id, String displayName, String agentComponentId) {}

  public record SampleList(List<SampleSummary> samples) {}

  private final ComponentClient componentClient;

  public RunControlEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/samples")
  public SampleList samples() {
    var entries = AgentRegistry.samples().stream()
      .map(e -> new SampleSummary(e.id(), e.displayName(), e.agentComponentId()))
      .toList();
    return new SampleList(entries);
  }
}
