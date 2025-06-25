package demo.multiagent.application;

import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

// tag::function-tool[]
public class WeatherServiceImpl implements WeatherService {
  // end::function-tool[]
  private final Logger logger = LoggerFactory.getLogger(WeatherServiceImpl.class);
  private final String WEATHER_API_KEY = "WEATHER_API_KEY";

  // tag::function-tool[]
  private final HttpClient httpClient;
  // end::function-tool[]
  public WeatherServiceImpl(HttpClientProvider httpClientProvider) {
    this.httpClient = httpClientProvider.httpClientFor("https://api.weatherapi.com");
  }

  @Override
  // tag::function-tool[]
  public String getWeather(String location, Optional<String> dateOptional) {

      var date = dateOptional
        .orElse(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

      var encodedLocation = java.net.URLEncoder.encode(location, StandardCharsets.UTF_8);
      var apiKey = System.getenv(WEATHER_API_KEY);
      String url = String.format("/v1/current.json?&q=%s&aqi=no&key=%s&dt=%s",
          encodedLocation, apiKey, date);
      return httpClient.GET(url).invoke().body().utf8String();
    }
}
// end::function-tool[]
