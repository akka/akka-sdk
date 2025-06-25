package demo.multiagent.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;
import demo.multiagent.domain.AgentRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// tag::description[]
@ComponentId("weather-agent")
@AgentDescription(
    name = "Weather Agent",
    description = """
      An agent that provides weather information. It can provide current weather, forecasts, and other
      related information.
    """,
    role = "worker"
)
// tag::function-tool[]
public class WeatherAgent extends Agent {
// end::description[]
// end::function-tool[]

  private static final String SYSTEM_MESSAGE = """
      You are a weather agent.
      Your job is to provide weather information.
      You provide current weather, forecasts, and other related information.

      The responses from the weather services are in json format. You need to digest
      it into human language. Be aware that Celsius temperature is in temp_c field.
      Fahrenheit temperature is in temp_f field.

      IMPORTANT:
      You return an error if the asked question is outside your domain of expertise,
      if it's invalid or if you cannot provide a response for any other reason.
      Start the error response with ERROR.
    """.stripIndent();


  // tag::function-tool[]
  private final WeatherService weatherService;

  public WeatherAgent(WeatherService weatherService) {
    this.weatherService = weatherService; // <1>
  }

  public Effect<String> query(AgentRequest request) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .tools(weatherService) // <2>
        .userMessage(request.message())
        .thenReply();
  }

  @FunctionTool(description = "Return current date in yyyy-MM-dd format") // <3>
  private String getCurrentDate() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }
}
// end::function-tool[]
