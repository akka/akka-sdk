package demo.multiagent.application.agents;

import akka.javasdk.http.HttpClientProvider;
import demo.multiagent.application.SessionMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Collection;
import java.util.List;

@AgentCard(
    id = "weather-agent",
    name = "Weather Agent",
    description = """
      An agent that provides weather information. It can provide current weather, forecasts, and other
      related information.
    """
)
public class WeatherAgent extends Agent {



  private final String sysMessage = """
      You are a weather agent.
      Your job is to provide weather information.
      You provide current weather, forecasts, and other related information.
    
      The responses from the weather services are in json format. You need to digest it into human language. Be aware that
      Celsius temperature is in temp_c field. Fahrenheit temperature is in temp_f field.
    """;

  private final HttpClientProvider httpClientProvider;

  public WeatherAgent(SessionMemory sessionMemory, ChatLanguageModel chatLanguageModel, HttpClientProvider httpClientProvider) {
    super(sessionMemory, chatLanguageModel);
    this.httpClientProvider = httpClientProvider;
  }


  @Override
  public String agentSpecificSystemMessage() {
    return sysMessage;
  }

  @Override
  public Collection<Object> availableTools() {
    return List.of(new WeatherService(httpClientProvider));
  }
}
