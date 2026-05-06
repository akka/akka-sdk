package demo.ui;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import demo.ui.api.RunControlEndpoint;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

public class RunControlEndpointSamplesTest extends TestKitSupport {

  @Test
  public void listsAllImplementedSamples() {
    var response = httpClient
      .GET("/playground/api/samples")
      .responseBodyAs(RunControlEndpoint.SampleList.class)
      .invoke()
      .body();

    var ids = response.samples().stream().map(RunControlEndpoint.SampleSummary::id).toList();
    assertThat(ids).containsExactlyInAnyOrderElementsOf(
      Set.of(
        "helloworld",
        "pipeline",
        "docreview",
        "dynamic",
        "research",
        "consulting",
        "support",
        "publishing",
        "debate",
        "negotiation",
        "peerreview",
        "devteam"
      )
    );

    var byId = response
      .samples()
      .stream()
      .collect(Collectors.toMap(RunControlEndpoint.SampleSummary::id, e -> e));
    assertThat(byId.get("helloworld").agentComponentId()).isEqualTo("question-answerer");
    assertThat(byId.get("debate").agentComponentId()).isEqualTo("debate-moderator");
    assertThat(byId.get("publishing").agentComponentId()).isEqualTo("content-agent");
  }
}
