/*
 * Copyright (C) 2021-2024 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

public sealed interface ModelProvider {
  /**
   * Model provider from configuration defined in {@code akka.javasdk.agent.model-provider}.
   */
  static ModelProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * Model provider from configuration defined in the given {@code configPath}.
   */
  static ModelProvider fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  record FromConfig(String configPath) implements ModelProvider {}

  /**
   * Settings for the Anthropic Large Language Model provider.
   */
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

  
  /**
   * Settings for the Anthropic Large Language Model provider.
   */
  record Anthropic(
      /** API key for authentication with Anthropic's API */
      String apiKey,
      /** Name of the Anthropic model to use (e.g. "claude-2") */
      String modelName,
      /** Base URL for Anthropic's API endpoints */
      String baseUrl,
      /** Controls randomness in the model's output (0.0-1.0, higher = more random) */
      double temperature,
      /**
       * Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
       * only considering the most likely tokens whose cumulative probability
       * exceeds the threshold value. It helps balance between diversity and
       * quality of outputs—lower values (like 0.3) produce more focused,
       * predictable text while higher values (like 0.9) allow more creativity
       * and variation.
       */
      double topP,
      /**
       * Top-k sampling limits text generation to only the k most probable
       * tokens at each step, discarding all other possibilities regardless
       * of their probability. It provides a simpler way to control randomness,
       * smaller k values (like 10) produce more focused outputs while larger
       * values (like 50) allow for more diversity.
       */
      int topK,
      /** Maximum number of tokens to generate in the response */
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

  /**
   * Settings for the Ollama Large Language Model provider.
   */
  static Ollama ollama() {
    return new Ollama("http://localhost:11434", "", Double.NaN, Double.NaN);
  }

  /**
   * Settings for the Ollama Large Language Model provider.
   */
  record Ollama(String baseUrl, String modelName, Double temperature, Double topP) implements ModelProvider {

    public static Ollama fromConfig(Config config) {
      return new Ollama(
          config.getString("base-url"),
          config.getString("model-name"),
          config.getDouble("temperature"),
          config.getDouble("top-p"));
    }

    public Ollama withBaseUrl(String baseUrl) {
      return new Ollama(baseUrl, modelName, temperature, topP);
    }

    public Ollama withModelName(String modelName) {
      return new Ollama(baseUrl, modelName, temperature, topP);
    }

    public Ollama withTemperature(double temperature) {
      return new Ollama(baseUrl, modelName, temperature, topP);
    }

    public Ollama withTopP(double topP) {
      return new Ollama(baseUrl, modelName, temperature, topP);
    }

  }

  /**
   * Settings for the Anthropic Large Language Model provider.
   */
  static OpenAi openAi() {
    return new OpenAi(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1);
  }

  /**
   * Settings for the OpenAI Large Language Model provider.
   */
  record OpenAi(
      /** API key for authentication with OpenAI's API */
      String apiKey,
      /** Name of the OpenAI model to use (e.g. "gpt-4") */
      String modelName,
      /** Base URL for OpenAI's API endpoints */
      String baseUrl,
      /** Controls randomness in the model's output (0.0-1.0, higher = more random) */
      double temperature,
      /**
       * Nucleus sampling parameter (0.0 to 1.0). Controls text generation by
       * only considering the most likely tokens whose cumulative probability
       * exceeds the threshold value. It helps balance between diversity and
       * quality of outputs—lower values (like 0.3) produce more focused,
       * predictable text while higher values (like 0.9) allow more creativity
       * and variation.
       */
      double topP,
      /** Maximum number of tokens to generate in the response */
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
    /**
     * @return an instance of {@code dev.langchain4j.model.chat.ChatModel}
     */
    Object createChatModel();

    /**
     * @return an instance of {@code dev.langchain4j.model.chat.StreamingChatModel}
     */
    Object createStreamingChatModel();
  }
}


