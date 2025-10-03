package agent_guide.part3;

// tag::all[]
import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component(id = "weather-agent")
public class WeatherAgent extends Agent {

  private static final String SYSTEM_MESSAGE = // <1>
    """
    You are a weather agent.
    Your job is to provide weather information.
    You provide current weather, forecasts, and other related information.

    The responses from the weather services are in json format. You need to digest
    it into human language. Be aware that Celsius temperature is in temp_c field.
    Fahrenheit temperature is in temp_f field.
    """.stripIndent();

  private final HttpClient httpClient;

  public WeatherAgent(HttpClientProvider httpClientProvider) { // <2>
    this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
  }

  public Effect<String> query(String message) {
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }

  @FunctionTool(description = "Returns the current weather forecast for a given city.")
  private String getCurrentWeather( // <3>
    @Description("A location or city name.") String location
  ) {
    var date = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

    var encodedLocation = java.net.URLEncoder.encode(location, StandardCharsets.UTF_8);
    var apiKey = System.getenv("WEATHER_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      throw new RuntimeException(
        "Make sure you have WEATHER_API_KEY defined as environment variable."
      );
    }

    String url = String.format(
      "/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
      encodedLocation,
      apiKey,
      date
    );
    return httpClient.GET(url).invoke().body().utf8String();
  }
}
// end::all[]
