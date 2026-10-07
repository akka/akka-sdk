/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.judgment;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The judgment model that answers the questions of a {@link JudgmentClient} request.
 *
 * <p>A {@link JudgmentClient} uses the provider configured in {@code
 * akka.javasdk.judgment.model-provider} unless you pass one to {@link
 * JudgmentClient#model(JudgmentModelProvider)}.
 */
public sealed interface JudgmentModelProvider
    permits JudgmentModelProvider.FromConfig,
        JudgmentModelProvider.SystemOne,
        JudgmentModelProvider.Custom {

  /** The provider configured in {@code akka.javasdk.judgment.model-provider}. */
  static FromConfig fromConfig() {
    return fromConfig("");
  }

  /**
   * The provider defined at a configuration path. A name without dots resolves under {@code
   * akka.javasdk.judgment}, so {@code fromConfig("system-one")} reads {@code
   * akka.javasdk.judgment.system-one}.
   */
  static FromConfig fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  /**
   * A provider defined in configuration.
   *
   * @param configPath the configuration path, empty for {@code
   *     akka.javasdk.judgment.model-provider}
   */
  record FromConfig(String configPath) implements JudgmentModelProvider {
    public FromConfig {
      Objects.requireNonNull(configPath, "configPath");
    }
  }

  /**
   * A SystemOne provider with the defaults of {@code akka.javasdk.judgment.system-one}. Set the API
   * key and the model name.
   */
  static SystemOne systemOne() {
    return new SystemOne(
        "",
        "",
        "https://api.typesafe.ai",
        Duration.ofSeconds(15),
        Duration.ofSeconds(30),
        2,
        List.of());
  }

  /**
   * A model that implements the SystemOne API. The request goes to {@code /v1/systemone} under the
   * base URL.
   *
   * @param apiKey the API key, sent as a bearer token
   * @param modelName the model version. Pin a version once you have tuned your thresholds, because
   *     an alias such as {@code jev-latest} can move to a new version.
   * @param baseUrl the base URL of the API
   * @param connectionTimeout fail the request when connecting takes longer than this
   * @param responseTimeout fail the request when the answer takes longer than this
   * @param maxRetries retry this many times on rate limit (429) and overload (529) responses
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

    /** Read the provider from a configuration section such as {@code system-one}. */
    public static SystemOne fromConfig(Config config) {
      return new SystemOne(
          config.getString("api-key"),
          config.getString("model-name"),
          config.getString("base-url"),
          config.getDuration("connection-timeout"),
          config.getDuration("response-timeout"),
          config.getInt("max-retries"),
          config.getStringList("additional-model-request-headers").stream()
              .map(SystemOne::parseHeader)
              .toList());
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

    private static HttpHeader parseHeader(String entry) {
      int colonIdx = entry.indexOf(':');
      if (colonIdx < 0)
        throw new IllegalArgumentException(
            "Invalid header format [" + entry + "], expected 'name:value'");
      return RawHeader.create(entry.substring(0, colonIdx), entry.substring(colonIdx + 1));
    }
  }

  /**
   * A provider that answers in the service process instead of calling a model, for example a mock
   * in tests.
   */
  non-sealed interface Custom extends JudgmentModelProvider {

    /**
     * Answer the request. The returned judgment must have an answer of the matching type for every
     * question, otherwise the client fails the request.
     */
    Judgment judge(Request request);
  }

  /**
   * A request as given to a {@link Custom} provider.
   *
   * @param state the state as passed to {@link JudgmentClient#state(Object)}
   * @param questions the questions in request order
   */
  record Request(Object state, List<Question<?>> questions) {
    public Request {
      Objects.requireNonNull(state, "state");
      questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
    }
  }
}
