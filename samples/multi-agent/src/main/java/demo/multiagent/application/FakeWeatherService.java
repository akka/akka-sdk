package demo.multiagent.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class FakeWeatherService implements WeatherService {


  private final Logger logger = LoggerFactory.getLogger(FakeWeatherService.class);

  @Override
  public String getWeather(String location, Optional<String> dateOptional) {
    logger.warn("Weather API Key not set, using a fake weather forecast");

    var date = dateOptional
      .orElse(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

    return "It's always sunny %s in %s.".formatted(date, location);
  }
}
