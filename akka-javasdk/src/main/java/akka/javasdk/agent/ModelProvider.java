/*
 * Copyright (C) 2021-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import com.typesafe.config.Config;
import java.time.Duration;
import java.util.Map;

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
    return new Anthropic(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1,
        -1,
        Duration.ofSeconds(15),
        Duration.ofMinutes(1),
        2,
        0);
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
      int maxTokens,
      /** Fail the request if connecting to the model API takes longer than this */
      Duration connectionTimeout,
      /**
       * Fail the request if getting a response from the model API takes longer than this, does not
       * apply to streaming agents
       */
      Duration responseTimeout,
      /** If the request fails, retry this many times. */
      int maxRetries,
      /** A maximum number of tokens to spend on thinking, use 0 to disable thinking */
      int thinkingBudgetTokens)
      implements ModelProvider {

    public static Anthropic fromConfig(Config config) {
      return new Anthropic(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("top-k"),
          config.getInt("max-tokens"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getInt("thinking-budget-tokens"));
    }

    public Anthropic withApiKey(String apiKey) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withModelName(String modelName) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withBaseUrl(String baseUrl) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withTemperature(double temperature) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withTopP(double topP) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withTopK(int topK) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withMaxTokens(int maxTokens) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withConnectionTimeout(Duration connectionTimeout) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withResponseTimeout(Duration responseTimeout) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withMaxRetries(int maxRetries) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }

    public Anthropic withThinkingBudgetTokens(int thinkingBudgetTokens) {
      return new Anthropic(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          topK,
          maxTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinkingBudgetTokens);
    }
  }

  /** Settings for the Google AI Gemini Large Language Model provider. */
  static GoogleAIGemini googleAiGemini() {
    return new GoogleAIGemini(
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1,
        Duration.ofSeconds(15),
        Duration.ofMinutes(1),
        2,
        "",
        0,
        "");
  }

  /** Settings for the Google AI Gemini Large Language Model provider. */
  record GoogleAIGemini(
      String apiKey,
      String modelName,
      Double temperature,
      Double topP,
      int maxOutputTokens,
      Duration connectionTimeout,
      Duration responseTimeout,
      int maxRetries,
      String baseUrl,
      int thinkingBudget,
      String thinkingLevel)
      implements ModelProvider {

    /**
     * @deprecated Use constructor with baseUrl parameter, or the static factory method and {@code
     *     with} methods.
     */
    @Deprecated
    public GoogleAIGemini(
        String apiKey,
        String modelName,
        Double temperature,
        Double topP,
        int maxOutputTokens,
        Duration connectionTimeout,
        Duration responseTimeout,
        int maxRetries) {
      this(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          "",
          0,
          "");
    }

    public static GoogleAIGemini fromConfig(Config config) {
      return new GoogleAIGemini(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-output-tokens"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getString("base-url"),
          config.getInt("thinking-budget"),
          config.getString("thinking-level"));
    }

    public GoogleAIGemini withApiKey(String apiKey) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withModelName(String modelName) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withBaseUrl(String baseUrl) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withTemperature(double temperature) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withTopP(double topP) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withMaxOutputTokens(int maxOutputTokens) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withConnectionTimeout(Duration connectionTimeout) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withResponseTimeout(Duration responseTimeout) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withMaxRetries(int maxRetries) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withThinkingBudget(int thinkingBudget) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
    }

    public GoogleAIGemini withThinkingLevel(String thinkingLevel) {
      return new GoogleAIGemini(
          apiKey,
          modelName,
          temperature,
          topP,
          maxOutputTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          baseUrl,
          thinkingBudget,
          thinkingLevel);
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
    return new Ollama(
        "http://localhost:11434",
        "",
        Double.NaN,
        Double.NaN,
        Duration.ofSeconds(15),
        Duration.ofMinutes(1),
        2,
        false);
  }

  /** Settings for the Ollama Large Language Model provider. */
  record Ollama(
      String baseUrl,
      String modelName,
      Double temperature,
      Double topP,
      Duration connectionTimeout,
      Duration responseTimeout,
      int maxRetries,
      boolean think)
      implements ModelProvider {

    public static Ollama fromConfig(Config config) {
      return new Ollama(
          config.getString("base-url"),
          config.getString("model-name"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getBoolean("think"));
    }

    public Ollama withBaseUrl(String baseUrl) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withModelName(String modelName) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withTemperature(double temperature) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withTopP(double topP) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withConnectionTimeout(Duration connectionTimeout) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withResponseTimeout(Duration responseTimeout) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withMaxRetries(int maxRetries) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }

    public Ollama withThink(boolean think) {
      return new Ollama(
          baseUrl,
          modelName,
          temperature,
          topP,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          think);
    }
  }

  /** Settings for the OpenAI Large Language Model provider. */
  static OpenAi openAi() {
    return new OpenAi(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1,
        -1,
        Duration.ofSeconds(15),
        Duration.ofMinutes(1),
        2,
        false);
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
      int maxCompletionTokens,
      Duration connectionTimeout,
      Duration responseTimeout,
      int maxRetries,
      boolean thinking)
      implements ModelProvider {

    public static OpenAi fromConfig(Config config) {
      return new OpenAi(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-tokens"),
          config.getInt("max-completion-tokens"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getBoolean("thinking"));
    }

    public OpenAi withApiKey(String apiKey) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withModelName(String modelName) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withBaseUrl(String baseUrl) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withTemperature(double temperature) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withTopP(double topP) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withMaxTokens(int maxTokens) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withMaxCompletionTokens(int maxCompletionTokens) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withConnectionTimeout(Duration connectionTimeout) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withResponseTimeout(Duration responseTimeout) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withMaxRetries(int maxRetries) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }

    public OpenAi withThinking(boolean thinking) {
      return new OpenAi(
          apiKey,
          modelName,
          baseUrl,
          temperature,
          topP,
          maxTokens,
          maxCompletionTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }
  }

  /** Settings for the HuggingFace Large Language Model provider. */
  static HuggingFace huggingFace() {
    // Implementation omitted for shortness
    return new HuggingFace(
        "",
        "",
        "",
        Double.NaN,
        Double.NaN,
        -1,
        Duration.ofSeconds(15),
        Duration.ofMinutes(1),
        2,
        false);
  }

  record HuggingFace(
      String accessToken,
      String modelId,
      String baseUrl,
      double temperature,
      double topP,
      int maxNewTokens,
      Duration connectionTimeout,
      Duration responseTimeout,
      int maxRetries,
      boolean thinking)
      implements ModelProvider {

    public static HuggingFace fromConfig(Config config) {
      return new HuggingFace(
          config.getString("access-token"),
          config.getString("model-id"),
          config.getString("base-url"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-new-tokens"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getBoolean("thinking"));
    }

    public HuggingFace withAccessToken(String accessToken) {
      return new HuggingFace(
          accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withModelId(String modelId) {
      return new HuggingFace(
          this.accessToken,
          modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withBaseUrl(String baseUrl) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withTemperature(Double temperature) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withTopP(Double topP) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withMaxNewTokens(Integer maxNewTokens) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withConnectionTimeout(Duration connectionTimeout) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          connectionTimeout,
          this.responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withResponseTimeout(Duration responseTimeout) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          responseTimeout,
          this.maxRetries,
          this.thinking);
    }

    public HuggingFace withMaxRetries(int maxRetries) {
      return new HuggingFace(
          this.accessToken,
          this.modelId,
          this.baseUrl,
          this.temperature,
          this.topP,
          this.maxNewTokens,
          this.connectionTimeout,
          this.responseTimeout,
          maxRetries,
          this.thinking);
    }

    public HuggingFace withThinking(boolean thinking) {
      return new HuggingFace(
          accessToken,
          modelId,
          baseUrl,
          temperature,
          topP,
          maxNewTokens,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          thinking);
    }
  }

  static Custom custom(Custom provider) {
    return provider;
  }

  /**
   * Custom models can be added by implementing this interface and, and the underlying
   * implementations of {@code dev.langchain4j.model.chat.ChatModel} and (optionally) {@code
   * dev.langchain4j.model.chat.StreamingChatModel}.
   *
   * <p>Refer to the Langchain4j documentation or reference implementations for how to implement the
   * {@code ChatModel} and {@code StreamingChatModel}.
   */
  non-sealed interface Custom extends ModelProvider {
    /**
     * @return an instance of {@code dev.langchain4j.model.chat.ChatModel}
     */
    Object createChatModel();

    /**
     * If you don't need streaming you can throw an exception from this method.
     *
     * @return an instance of {@code dev.langchain4j.model.chat.StreamingChatModel}
     */
    Object createStreamingChatModel();
  }

  /** Settings for the Bedrock Large Language Model provider. */
  static Bedrock bedrock() {
    return new Bedrock(
        "",
        "",
        false,
        false,
        -1,
        -1,
        Map.of(),
        "",
        Double.NaN,
        Double.NaN,
        -1,
        Duration.ofMinutes(1),
        2);
  }

  record Bedrock(
      String region,
      String modelId,
      boolean returnThinking,
      boolean sendThinking,
      int maxOutputTokens,
      int reasoningTokenBudget,
      Map<String, Object> additionalModelRequestFields,
      String accessToken,
      double temperature,
      double topP,
      int maxTokens,
      Duration responseTimeout,
      int maxRetries)
      implements ModelProvider {

    public static Bedrock fromConfig(Config config) {
      return new Bedrock(
          config.getString("region"),
          config.getString("model-id"),
          false,
          false,
          config.getInt("max-output-tokens"),
          config.getInt("reasoning-token-budget"),
          config.getConfig("additional-model-request-fields").root().unwrapped(),
          config.getString("access-token"),
          config.getDouble("temperature"),
          config.getDouble("top-p"),
          config.getInt("max-tokens"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"));
    }

    public Bedrock withRegion(String region) {
      return new Bedrock(
          region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withModelId(String modelId) {
      return new Bedrock(
          this.region,
          modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withReturnThinking(Boolean returnThinking) {
      return new Bedrock(
          this.region,
          this.modelId,
          returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withSendThinking(Boolean sendThinking) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withMaxOutputTokens(int maxOutputTokens) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withReasoningTokenBudget(int reasoningTokenBudget) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withAdditionalModelRequestFields(
        Map<String, Object> additionalModelRequestFields) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withAccessToken(String accessToken) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withTemperature(double temperature) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withTopP(double topP) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          topP,
          this.maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withMaxTokens(int maxTokens) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          maxTokens,
          this.responseTimeout,
          this.maxRetries);
    }

    public Bedrock withResponseTimeout(Duration responseTimeout) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          responseTimeout,
          this.maxRetries);
    }

    public Bedrock withMaxRetries(int maxRetries) {
      return new Bedrock(
          this.region,
          this.modelId,
          this.returnThinking,
          this.sendThinking,
          this.maxOutputTokens,
          this.reasoningTokenBudget,
          this.additionalModelRequestFields,
          this.accessToken,
          this.temperature,
          this.topP,
          this.maxTokens,
          this.responseTimeout,
          maxRetries);
    }
  }
}
