package demo.multiagent.application.agents;

import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class WeatherService {

  private final Logger logger = LoggerFactory.getLogger(WeatherService.class);
  private final String WEATHER_API_KEY = "WEATHER_API_KEY";

  private HttpClient httpClient;

  public WeatherService(HttpClientProvider httpClientProvider) {
    if (System.getenv(WEATHER_API_KEY) != null && !System.getenv(WEATHER_API_KEY).isEmpty()) {
      this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
    }
  }

  public String getWeather(String location, String date) {

    logger.info("Getting weather forecast for city {} on {}", location, date);

    if (httpClient == null) {
      logger.warn("Weather API Key not set, using a fake weather forecast");
      return "It's always sunny %s at %s.".formatted(date, location);
    } else {

      var encodedLocation = java.net.URLEncoder.encode(location, StandardCharsets.UTF_8);

      var apiKey = System.getenv(WEATHER_API_KEY);
      String url = String.format("/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
          encodedLocation, apiKey, date);

      return httpClient.GET(url).invoke().body().utf8String();
    }
  }
}
