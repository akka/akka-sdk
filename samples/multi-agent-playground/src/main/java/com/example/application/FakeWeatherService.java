package com.example.application;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeWeatherService implements WeatherService {

  private final Logger logger = LoggerFactory.getLogger(FakeWeatherService.class);
  private final Random random = new Random();

  private static final List<String> CONDITIONS = List.of(
    "sunny",
    "partly cloudy",
    "cloudy",
    "rainy",
    "stormy",
    "snowy",
    "foggy",
    "windy"
  );

  private static final List<String> DETAILS = List.of(
    "with clear skies",
    "with scattered clouds",
    "with overcast skies",
    "with light rain showers",
    "with heavy rain",
    "with thunderstorms possible",
    "with light snow",
    "with reduced visibility",
    "with strong winds",
    "with a gentle breeze"
  );

  @Override
  public String getWeather(String location, Optional<String> dateOptional) {
    logger.info("Using simulated weather forecast for {}", location);

    var date = dateOptional.orElse(
      LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    );

    // Generate random weather
    String condition = CONDITIONS.get(random.nextInt(CONDITIONS.size()));
    String detail = DETAILS.get(random.nextInt(DETAILS.size()));
    int tempC = 5 + random.nextInt(30); // Temperature between 5°C and 35°C
    int tempF = (int) ((tempC * 9.0) / 5.0 + 32);
    int humidity = 30 + random.nextInt(60); // Humidity between 30% and 90%
    int windKph = random.nextInt(40); // Wind speed 0-40 kph

    return String.format(
      "Weather for %s on %s: %s %s. Temperature: %d°C (%d°F). " +
      "Humidity: %d%%. Wind: %d kph.",
      location,
      date,
      condition,
      detail,
      tempC,
      tempF,
      humidity,
      windKph
    );
  }
}
