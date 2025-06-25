package agent_guide.part3;

// tag::class[]
import akka.javasdk.testkit.TestKitSupport;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class WeatherAgentIntegrationTest extends TestKitSupport { // <1>

  public void testAgent() {
    var sessionId = UUID.randomUUID().toString();
    var message = "I am in Madrid";
    var forecast =
        componentClient
        .forAgent()
        .inSession(sessionId)
        .method(WeatherAgent::query) // <2>
        .invoke(message);

    System.out.println(forecast); // <3>
    assertThat(forecast).isNotBlank();
  }
}
// end::class[]
