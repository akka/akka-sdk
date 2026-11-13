package demo.activities.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "weather-agent")
public class WeatherAgent extends Agent {

  private static final Logger logger = LoggerFactory.getLogger(WeatherAgent.class);

  private static final String SYSTEM_MESSAGE =
    """
    You are a weather agent.
    Your job is to provide weather information for the location in the user's message.
    Use the weather tool. Answer in one or two sentences.

    The weather service answers in json format. Be aware that Celsius temperature is in
    the temp_c field and Fahrenheit temperature is in the temp_f field.
    """.stripIndent();

  // Used when WEATHER_API_KEY is not set, so that different cities give different weather.
  private static final List<String> FAKE_WEATHER = List.of(
    "Sunny and 24 °C",
    "Heavy rain and 9 °C",
    "Cloudy with light wind and 15 °C",
    "Snow and -3 °C"
  );

  private final HttpClient httpClient;

  public WeatherAgent(HttpClientProvider httpClientProvider) {
    this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
  }

  public Effect<String> query(String message) {
    logger.info("Invoked with: {}", message);
    return effects().systemMessage(SYSTEM_MESSAGE).userMessage(message).thenReply();
  }

  @FunctionTool(description = "Returns the current weather for a given city.")
  private String getCurrentWeather(@Description("A location or city name.") String location) {
    var apiKey = System.getenv("WEATHER_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      var index = Math.floorMod(
        location.toLowerCase(Locale.ROOT).hashCode(),
        FAKE_WEATHER.size()
      );
      var fake = FAKE_WEATHER.get(index) + " in " + location + " today.";
      logger.warn("WEATHER_API_KEY is not set, using fake weather: {}", fake);
      return fake;
    }

    var url = String.format(
      "/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
      URLEncoder.encode(location, StandardCharsets.UTF_8),
      apiKey,
      LocalDate.now()
    );
    return httpClient.GET(url).invoke().body().utf8String();
  }
}
