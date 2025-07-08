package agent_guide.part3;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

// tag::all[]
import akka.javasdk.testkit.TestKitSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WeatherAgentIntegrationTest extends TestKitSupport { // <1>

  @Test
  public void testAgent() {
    var sessionId = UUID.randomUUID().toString();
    var message = "I am in Madrid";
    var forecast = componentClient
      .forAgent()
      .inSession(sessionId)
      .method(WeatherAgent::query) // <2>
      .invoke(message);

    System.out.println(forecast); // <3>
    assertThat(forecast).isNotBlank();
  }
}
// end::all[]
