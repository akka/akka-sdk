package demo.multiagent;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.http.HttpClientProvider;
import com.typesafe.config.Config;
import demo.multiagent.application.FakeWeatherService;
import demo.multiagent.application.WeatherService;
import demo.multiagent.application.WeatherServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String WEATHER_API_KEY = "WEATHER_API_KEY";
  private final HttpClientProvider httpClientProvider;

  public Bootstrap(Config config, HttpClientProvider httpClientProvider) {
    this.httpClientProvider = httpClientProvider;

    if (config.getString("akka.javasdk.agent.openai.api-key").isBlank()) {
      logger.error(
        "No API keys found. Make sure you have OPENAI_API_KEY defined as environment variable.");
      throw new RuntimeException("No API keys found.");
    }
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {

        if (clazz == WeatherService.class) {
          if (System.getenv(WEATHER_API_KEY) != null && !System.getenv(WEATHER_API_KEY).isEmpty()) {
            return (T) new WeatherServiceImpl(httpClientProvider);
          } else {
            // If the API key is not set, return a fake implementation
            return (T) new FakeWeatherService();
          }
        }
        return null;
      }
    };
  }
}

