package demo.ui;

import static org.assertj.core.api.Assertions.assertThat;

import akka.http.javadsl.model.StatusCodes;
import akka.javasdk.testkit.TestKitSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class RunNotificationEndpointIntegrationTest extends TestKitSupport {

  @Test
  public void returns404ForUnknownComponent() {
    var response = httpClient
      .GET("/playground/api/runs/" + UUID.randomUUID() + "/events?component=does-not-exist")
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.NOT_FOUND);
  }

  @Test
  public void returns400WhenComponentMissing() {
    var response = httpClient
      .GET("/playground/api/runs/" + UUID.randomUUID() + "/events")
      .invoke();
    assertThat(response.status()).isEqualTo(StatusCodes.BAD_REQUEST);
  }
  // Note: a content-type smoke test for the live SSE stream is intentionally omitted —
  // httpClient.invoke() materialises the body strictly and would hang on a long-lived stream.
  // The wire shape is unit-tested in TierClassifierTest; live behaviour is covered manually
  // in quickstart.md §2.
}
