package demo.multiagent;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.http.HttpClientProvider;
import com.typesafe.config.Config;
import demo.multiagent.application.agents.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Setup
public class Bootstrap implements ServiceSetup {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private final String OPENAI_API_KEY = "OPENAI_API_KEY";

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
          return (T) new WeatherService(httpClientProvider);
        }
        return null;
      }
    };
  }
}

