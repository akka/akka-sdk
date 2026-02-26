package com.example;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.http.HttpClientProvider;
import com.example.application.DateTools;
import com.example.application.FakeWeatherService;
import com.example.application.WeatherService;
import com.example.application.WebFetchService;
import com.example.application.WebSearchService;
import com.typesafe.config.Config;

@Setup
public class Bootstrap implements ServiceSetup {

  private final HttpClientProvider httpClientProvider;
  private final Config config;

  public Bootstrap(Config config, HttpClientProvider httpClientProvider) {
    this.config = config;
    if (
      config.getString("akka.javasdk.agent.model-provider").equals("googleai-gemini") &&
      config.getString("akka.javasdk.agent.googleai-gemini.api-key").isBlank()
    ) {
      throw new IllegalStateException(
        "No API keys found. Make sure you have GOOGLE_AI_GEMINI_API_KEY defined as environment variable, or change the model provider configuration in application.conf to use a different LLM."
      );
    }

    this.httpClientProvider = httpClientProvider;
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    WebSearchService webSearchService = new WebSearchService(
      httpClientProvider.httpClientFor("https://www.googleapis.com"),
      config.getString("google-search.api-key"),
      config.getString("google-search.search-engine-id")
    );
    WebFetchService webFetchService = new WebFetchService(httpClientProvider);

    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> clazz) {
        if (clazz == WeatherService.class) {
          return (T) new FakeWeatherService();
        } else if (clazz == DateTools.class) {
          return (T) new DateTools();
        } else if (clazz == WebSearchService.class) {
          return (T) webSearchService;
        } else if (clazz == WebFetchService.class) {
          return (T) webFetchService;
        }
        return null;
      }
    };
  }
}
