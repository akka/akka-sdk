package demo.multiagent.application;

import static org.assertj.core.api.Assertions.assertThat;

// tag::class[]
import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import akka.javasdk.testkit.TestModelProvider.AiResponse;
import akka.javasdk.testkit.TestModelProvider.ToolInvocationRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class WeatherAgentIntegrationTest extends TestKitSupport { // <1>

  private final TestModelProvider weatherModel = new TestModelProvider(); // <2>

  @Override
  protected TestKit.Settings testKitSettings() {
    return TestKit.Settings.DEFAULT.withAdditionalConfig(
      "akka.javasdk.agent.openai.api-key = n/a"
    ).withModelProvider(WeatherAgent.class, weatherModel); // <3>
  }

  // tag::fixed-response[]
  @Test
  public void replyWithFixedResponse() {
    weatherModel.fixedResponse("The weather in Madrid is sunny, 25°C."); // <4>

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(WeatherAgent::query)
      .invoke("What is the weather in Madrid?");

    assertThat(reply).contains("sunny");
  }

  // end::fixed-response[]

  // tag::tool-call[]
  // The runtime prefixes a tool name with the simple name of the registered tool's class;
  // here the WeatherAgent receives a FakeWeatherService instance (wired by the bootstrap
  // when WEATHER_API_KEY is unset), so the tool name the model sees is "FakeWeatherService_getWeather".
  private static final String GET_WEATHER_TOOL = "FakeWeatherService_getWeather";

  @Test
  public void invokeWeatherTool() {
    // Turn 1: the mocked model asks the runtime to invoke the getWeather tool.
    weatherModel
      .whenMessage(msg -> msg.contains("Stockholm"))
      .reply(new ToolInvocationRequest(GET_WEATHER_TOOL, "{\"location\":\"Stockholm\"}")); // <5>

    // Turn 2: the mocked model receives the tool result and produces the final answer.
    // FakeWeatherService returns "It's always sunny <date> in <location>."
    weatherModel
      .whenToolResult(tr -> tr.name().equals(GET_WEATHER_TOOL))
      .thenReply(tr -> new AiResponse("Forecast: " + tr.content())); // <6>

    var reply = componentClient
      .forAgent()
      .inSession(UUID.randomUUID().toString())
      .method(WeatherAgent::query)
      .invoke("What is the weather in Stockholm?");

    assertThat(reply).startsWith("Forecast:");
    assertThat(reply).contains("sunny");
    assertThat(reply).contains("Stockholm");
  }
  // end::tool-call[]
}
// end::class[]
