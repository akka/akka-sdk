/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.agent;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * The model that answers structured judgment requests, see {@link Agent.Effect.Builder#judgment()}.
 *
 * <p>SystemOne is the API these models implement. The TypeSafe Jev API is the default endpoint. An
 * agent that does not name a provider uses the one configured in {@code
 * akka.javasdk.agent.judgment-model-provider}.
 */
public sealed interface JudgmentModelProvider
    permits JudgmentModelProvider.FromConfig,
        JudgmentModelProvider.SystemOne,
        JudgmentModelProvider.Custom {

  /** The provider configured in {@code akka.javasdk.agent.judgment-model-provider}. */
  static JudgmentModelProvider fromConfig() {
    return fromConfig("");
  }

  /**
   * The provider at a configuration path. A bare name resolves under {@code akka.javasdk.agent}.
   */
  static JudgmentModelProvider fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  record FromConfig(String configPath) implements JudgmentModelProvider {}

  /** A SystemOne provider with the defaults of {@code akka.javasdk.agent.system-one}. */
  static SystemOne systemOne() {
    return new SystemOne(
        "",
        "jev-latest",
        "https://api.typesafe.ai",
        Duration.ofSeconds(15),
        Duration.ofSeconds(30),
        2,
        List.of());
  }

  /**
   * A SystemOne API endpoint.
   *
   * @param apiKey the API key sent as a bearer token
   * @param modelName the model, for example {@code jev-latest}
   * @param baseUrl the base URL of the API, the request goes to {@code /v1/systemone} under it
   * @param connectionTimeout fail the request when connecting takes longer than this
   * @param responseTimeout fail the request when the answer takes longer than this
   * @param maxRetries retry this many times on rate limit and overload responses
   * @param additionalModelRequestHeaders HTTP headers added to each request
   */
  record SystemOne(
      String apiKey,
      String modelName,
      String baseUrl,
      Duration connectionTimeout,
      Duration responseTimeout,
      int maxRetries,
      List<HttpHeader> additionalModelRequestHeaders)
      implements JudgmentModelProvider {

    public SystemOne {
      Objects.requireNonNull(apiKey, "apiKey");
      Objects.requireNonNull(modelName, "modelName");
      Objects.requireNonNull(baseUrl, "baseUrl");
      Objects.requireNonNull(connectionTimeout, "connectionTimeout");
      Objects.requireNonNull(responseTimeout, "responseTimeout");
      additionalModelRequestHeaders =
          List.copyOf(
              Objects.requireNonNull(
                  additionalModelRequestHeaders, "additionalModelRequestHeaders"));
    }

    public static SystemOne fromConfig(Config config) {
      return new SystemOne(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          headersFromConfig(config));
    }

    public SystemOne withApiKey(String apiKey) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withModelName(String modelName) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withBaseUrl(String baseUrl) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withConnectionTimeout(Duration connectionTimeout) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withResponseTimeout(Duration responseTimeout) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withMaxRetries(int maxRetries) {
      return new SystemOne(
          apiKey,
          modelName,
          baseUrl,
          connectionTimeout,
          responseTimeout,
          maxRetries,
          additionalModelRequestHeaders);
    }

    public SystemOne withAdditionalModelRequestHeaders(List<HttpHeader> headers) {
      return new SystemOne(
          apiKey, modelName, baseUrl, connectionTimeout, responseTimeout, maxRetries, headers);
    }
  }

  /**
   * A provider that answers in process. The testkit implements it to answer without a model. A
   * service can implement it and name the class in {@code provider} of its configuration section.
   */
  non-sealed interface Custom extends JudgmentModelProvider {

    /**
     * Answer the request. Every question in the request must get an answer of its type, otherwise
     * the call fails with a {@link ModelException}. An empty model name or a null token usage is
     * replaced by the provider's model name and zero usage.
     */
    Judgment judge(JudgmentRequest request);

    /** The model name reported in the interaction log and in telemetry. */
    default String modelName() {
      return "custom";
    }
  }

  private static HttpHeader parseHeaderEntry(String entry) {
    int colonIdx = entry.indexOf(':');
    if (colonIdx < 0)
      throw new IllegalArgumentException(
          "Invalid header format [" + entry + "], expected 'name:value'");
    return RawHeader.create(entry.substring(0, colonIdx), entry.substring(colonIdx + 1));
  }

  private static List<HttpHeader> headersFromConfig(Config config) {
    return config.getStringList("additional-model-request-headers").stream()
        .map(JudgmentModelProvider::parseHeaderEntry)
        .collect(Collectors.toList());
  }
}
