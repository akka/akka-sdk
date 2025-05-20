/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

public sealed interface ModelProvider {
  static ModelProvider fromConfig() {
    return fromConfig("");
  }

  static ModelProvider fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  record FromConfig(String configPath) implements ModelProvider {}

  static Anthropic anthropic() {
    return new Anthropic(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1,
        -1);
  }

  record Anthropic(String apiKey,
                   String modelName,
                   String baseUrl,
                   double temperature,
                   double topP,
                   int topK,
                   int maxTokens) implements ModelProvider {

    public static Anthropic fromConfig(Config config) {
      return new Anthropic(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("top-k"),
          config.getInt("max-tokens"));
    }
    
    public Anthropic withApiKey(String apiKey) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }
    
    public Anthropic withModelName(String modelName) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }
    
    public Anthropic withBaseUrl(String baseUrl) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }
    
    public Anthropic withTemperature(double temperature) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }
    
    public Anthropic withTopP(double topP) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }

    public Anthropic withTopK(int topK) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }

    public Anthropic withMaxTokens(int maxTokens) {
      return new Anthropic(apiKey, modelName, baseUrl, temperature, topP, topK, maxTokens);
    }
  }
  
  

  static OpenAi openAi() {
    return new OpenAi(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1);
  }

  record OpenAi(String apiKey,
                String modelName,
                String baseUrl,
                double temperature,
                double topP,
                int maxTokens) implements ModelProvider {

    public static OpenAi fromConfig(Config config) {
      return new OpenAi(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-tokens"));
    }
    
    public OpenAi withApiKey(String apiKey) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
    
    public OpenAi withModelName(String modelName) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
    
    public OpenAi withBaseUrl(String baseUrl) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
    
    public OpenAi withTemperature(double temperature) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
    
    public OpenAi withTopP(double topP) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
    
    public OpenAi withMaxTokens(int maxTokens) {
      return new OpenAi(apiKey, modelName, baseUrl, temperature, topP, maxTokens);
    }
  }
  
  static Custom custom(Custom provider) {
    return provider;
  }

  non-sealed interface Custom extends ModelProvider {
    Object createChatModel();
  }
}


