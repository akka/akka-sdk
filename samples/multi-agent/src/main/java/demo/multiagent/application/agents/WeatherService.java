package demo.multiagent.application.agents;

import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

// tag::function-tool[]
public class WeatherService {
  // end::function-tool[]
  private final Logger logger = LoggerFactory.getLogger(WeatherService.class);
  private final String WEATHER_API_KEY = "WEATHER_API_KEY";

  // tag::function-tool[]
  private HttpClient httpClient;

  public WeatherService(HttpClientProvider httpClientProvider) {
    // end::function-tool[]
    if (System.getenv(WEATHER_API_KEY) != null && !System.getenv(WEATHER_API_KEY).isEmpty()) {
      // tag::function-tool[]
      this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
      // end::function-tool[]
    }
    // tag::function-tool[]
  }

  @FunctionTool(description = "Returns the weather forecast for a given city.") // <1>
  public String getWeather(
    @Description("A location or city name.")
    String location, // <2>
    @Description("Forecast for a given date, in yyyy-MM-dd format.")
    String date) {
  // end::function-tool[]
    logger.info("Getting weather forecast for city {} on {}", location, date);

    if (httpClient == null) {
      logger.warn("Weather API Key not set, using a fake weather forecast");
      return "It's always sunny %s at %s.".formatted(date, location);
    } else {
      // tag::function-tool[]
      var encodedLocation = java.net.URLEncoder.encode(location, StandardCharsets.UTF_8);

      var apiKey = System.getenv(WEATHER_API_KEY);
      String url = String.format("/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
          encodedLocation, apiKey, date);

      return httpClient.GET(url).invoke().body().utf8String();
      // end::function-tool[]
    }
    // tag::function-tool[]
  }
  // end::function-tool[]
}
