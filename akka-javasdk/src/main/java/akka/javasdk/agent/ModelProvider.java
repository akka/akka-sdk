/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;

/**
 * Configuration interface for AI model providers used by agents.
 *
 * <p>ModelProvider defines which AI model and settings to use for agent interactions. Akka supports
 * multiple model providers including hosted services (OpenAI, Anthropic, Google AI Gemini,
 * HuggingFace) and locally running models (Ollama, LocalAI).
 *
 * <p>Different agents can use different models by specifying the ModelProvider in the agent effect.
 * If no model is specified, the default model from configuration is used.
 */
public sealed interface ModelProvider {

  /**
   * Creates a model provider from the default configuration path.
   *
   * <p>Reads configuration from {@code akka.javasdk.agent.model-provider}.
   *
   * @return a configuration-based model provider
   */
  static ModelProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * Creates a model provider from the specified configuration path.
   *
   * <p>Allows using different model configurations for different agents by defining multiple model
   * configurations and referencing them by path.
   *
   * @param configPath the configuration path to read model settings from
   * @return a configuration-based model provider
   */
  static ModelProvider fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  record FromConfig(String configPath) implements ModelProvider {}

  /** Settings for the Anthropic Large Language Model provider. */
  static Anthropic anthropic() {
    return new Anthropic("", "", "", Double.NaN, Double.NaN, -1, -1);
  }

  /** Settings for the Anthropic Large Language Model provider. */
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
       * Nucleus sampling parameter (0.0 to 1.0). Controls text generation by only considering the
       * most likely tokens whose cumulative probability exceeds the threshold value. It helps
       * balance between diversity and quality of outputs—lower values (like 0.3) produce more
       * focused, predictable text while higher values (like 0.9) allow more creativity and
       * variation.
       */
      double topP,
      /**
       * Top-k sampling limits text generation to only the k most probable tokens at each step,
       * discarding all other possibilities regardless of their probability. It provides a simpler
       * way to control randomness, smaller k values (like 10) produce more focused outputs while
       * larger values (like 50) allow for more diversity.
       */
      int topK,
      /** Maximum number of tokens to generate in the response */
      int maxTokens)
      implements ModelProvider {

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

  /** Settings for the Google AI Gemini Large Language Model provider. */
  static GoogleAIGemini googleAiGemini() {
    return new GoogleAIGemini("", "", Double.NaN, Double.NaN, -1);
  }

  /** Settings for the Google AI Gemini Large Language Model provider. */
  record GoogleAIGemini(
      String apiKey, String modelName, Double temperature, Double topP, int maxOutputTokens)
      implements ModelProvider {

    public static GoogleAIGemini fromConfig(Config config) {
      return new GoogleAIGemini(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-output-tokens"));
    }

    public GoogleAIGemini withApiKey(String apiKey) {
      return new GoogleAIGemini(apiKey, modelName, temperature, topP, maxOutputTokens);
    }

    public GoogleAIGemini withModelName(String modelName) {
      return new GoogleAIGemini(apiKey, modelName, temperature, topP, maxOutputTokens);
    }

    public GoogleAIGemini withTemperature(double temperature) {
      return new GoogleAIGemini(apiKey, modelName, temperature, topP, maxOutputTokens);
    }

    public GoogleAIGemini withTopP(double topP) {
      return new GoogleAIGemini(apiKey, modelName, temperature, topP, maxOutputTokens);
    }

    public GoogleAIGemini withMaxOutputTokens(int maxOutputTokens) {
      return new GoogleAIGemini(apiKey, modelName, temperature, topP, maxOutputTokens);
    }
  }

  /** Settings for the Local AI Large Language Model provider. */
  static LocalAI localAI() {
    return new LocalAI("http://localhost:8080/v1", "", Double.NaN, Double.NaN, -1);
  }

  /** Settings for the Local AI Large Language Model provider. */
  record LocalAI(String baseUrl, String modelName, Double temperature, Double topP, int maxTokens)
      implements ModelProvider {
    public static LocalAI fromConfig(Config config) {
      return new LocalAI(
          config.getString("base-url"),
          config.getString("model-name"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-tokens"));
    }

    public LocalAI withModelName(String modelName) {
      return new LocalAI(baseUrl, modelName, temperature, topP, maxTokens);
    }

    public LocalAI withTemperature(double temperature) {
      return new LocalAI(baseUrl, modelName, temperature, topP, maxTokens);
    }

    public LocalAI withTopP(double topP) {
      return new LocalAI(baseUrl, modelName, temperature, topP, maxTokens);
    }

    public LocalAI withMaxTokens(int maxTokens) {
      return new LocalAI(baseUrl, modelName, temperature, topP, maxTokens);
    }
  }

  /** Settings for the Ollama Large Language Model provider. */
  static Ollama ollama() {
    return new Ollama("http://localhost:11434", "", Double.NaN, Double.NaN);
  }

  /** Settings for the Ollama Large Language Model provider. */
  record Ollama(String baseUrl, String modelName, Double temperature, Double topP)
      implements ModelProvider {

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

  /** Settings for the OpenAI Large Language Model provider. */
  static OpenAi openAi() {
    return new OpenAi("", "", "", Double.NaN, Double.NaN, -1, -1);
  }

  /** Settings for the OpenAI Large Language Model provider. */
  record OpenAi(
      /** API key for authentication with OpenAI's API */
      String apiKey,
      /** Name of the OpenAI model to use (e.g. "gpt-4") */
      String modelName,
      /** Base URL for OpenAI's API endpoints */
      String baseUrl,
      /**
       * Controls randomness in the model's output (0.0-1.0, higher = more random). Not supported by
       * GPT-5.
       */
      double temperature,
      /**
       * Nucleus sampling parameter (0.0 to 1.0). Controls text generation by only considering the
       * most likely tokens whose cumulative probability exceeds the threshold value. It helps
       * balance between diversity and quality of outputs—lower values (like 0.3) produce more
       * focused, predictable text while higher values (like 0.9) allow more creativity and
       * variation. Not supported by GPT-5.
       */
      double topP,
      /**
       * Maximum number of tokens to generate in the response. Not supported by GPT-5, use
       * maxCompletionTokens instead.
       */
      int maxTokens,
      int maxCompletionTokens)
      implements ModelProvider {

    public static OpenAi fromConfig(Config config) {
      return new OpenAi(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-tokens"),
          config.getInt("max-completion-tokens"));
    }

    public OpenAi withApiKey(String apiKey) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withModelName(String modelName) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withBaseUrl(String baseUrl) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withTemperature(double temperature) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withTopP(double topP) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withMaxTokens(int maxTokens) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }

    public OpenAi withMaxCompletionTokens(int maxCompletionTokens) {
      return new OpenAi(
          apiKey, modelName, baseUrl, temperature, topP, maxTokens, maxCompletionTokens);
    }
  }

  /** Settings for the HuggingFace Large Language Model provider. */
  static HuggingFace huggingFace() {
    // Implementation omitted for shortness
    return new HuggingFace("", "", "", Double.NaN, Double.NaN, -1);
  }

  record HuggingFace(
      String accessToken,
      String modelId,
      String baseUrl,
      double temperature,
      double topP,
      int maxNewTokens)
      implements ModelProvider {

    public static HuggingFace fromConfig(Config config) {
      return new HuggingFace(
          config.getString("access-token"),
          config.getString("model-id"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-new-tokens"));
    }

    public HuggingFace withAccessToken(String accessToken) {
      return new HuggingFace(
          accessToken, this.modelId, this.baseUrl, this.temperature, this.topP, this.maxNewTokens);
    }

    public HuggingFace withModelId(String modelId) {
      return new HuggingFace(
          this.accessToken, modelId, this.baseUrl, this.temperature, this.topP, this.maxNewTokens);
    }

    public HuggingFace withBaseUrl(String baseUrl) {
      return new HuggingFace(
          this.accessToken, this.modelId, baseUrl, this.temperature, this.topP, this.maxNewTokens);
    }

    public HuggingFace withTemperature(Double temperature) {
      return new HuggingFace(
          this.accessToken, this.modelId, this.baseUrl, temperature, this.topP, this.maxNewTokens);
    }

    public HuggingFace withTopP(Double topP) {
      return new HuggingFace(
          this.accessToken, this.modelId, this.baseUrl, this.temperature, topP, this.maxNewTokens);
    }

    public HuggingFace withMaxNewTokens(Integer maxNewTokens) {
      return new HuggingFace(
          this.accessToken, this.modelId, this.baseUrl, this.temperature, this.topP, maxNewTokens);
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
