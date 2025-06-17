package demo.multiagent.application.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FakeWeatherService implements WeatherService {


  private final Logger logger = LoggerFactory.getLogger(WeatherServiceImpl.class);

  @Override
  public String getWeather(String location, String date) {
    logger.warn("Weather API Key not set, using a fake weather forecast");
    return "It's always sunny %s at %s.".formatted(date, location);
  }
}
