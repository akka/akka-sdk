package demo.multiagent.application.agents;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.AgentDescription;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClientProvider;
import demo.multiagent.domain.AgentResponse;
import dev.langchain4j.agent.tool.Tool;

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

      The responses from the weather services are in json format. You need to digest it into human language. Be aware that
      Celsius temperature is in temp_c field. Fahrenheit temperature is in temp_f field.
    """.stripIndent() + AgentResponse.FORMAT_INSTRUCTIONS;

  private final HttpClientProvider httpClientProvider;
    private final WeatherService weatherService;

  public WeatherAgent(HttpClientProvider httpClientProvider, WeatherService weatherService) {
    this.httpClientProvider = httpClientProvider;
    this.weatherService = weatherService;
  }

  public Agent.Effect<AgentResponse> query(String message) {
    return effects()
        .systemMessage(SYSTEM_MESSAGE)
        .userMessage(message)
        .responseAs(AgentResponse.class)
        .thenReply();
  }


  // tag::function-tool[]
  @FunctionTool("Returns the weather forecast for a given city.") // <1>
  private String getWeather(
      @Description("A location or city name.") String location, // <2>
      @Description("Forecast for a given date, in yyyy-MM-dd format. Leave empty to use current date.") String date) {
    var forecastDate = date;
    if (date == null || date.isBlank()) {
      forecastDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    return weatherService.getWeather(location, forecastDate); // <3>
  }
  // end::function-tool[]

  @FunctionTool("Return current date in yyyy-MM-dd format")
  private String getCurrentDate() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
  }
// tag::function-tool[]
}
// end::function-tool[]
