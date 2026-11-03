/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.decision;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.headers.RawHeader;
import com.typesafe.config.Config;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * The decision model that answers the questions of a {@link DecisionModelClient} request.
 *
 * <p>A {@link DecisionModelClient} uses the provider configured in {@code
 * akka.javasdk.decision.model-provider} unless you pass one to {@link
 * DecisionModelClient#model(DecisionModelProvider)}.
 */
public sealed interface DecisionModelProvider
    permits DecisionModelProvider.FromConfig,
        DecisionModelProvider.SystemOne,
        DecisionModelProvider.Custom {

  /** The provider configured in {@code akka.javasdk.decision.model-provider}. */
  static FromConfig fromConfig() {
    return fromConfig("");
  }

  /**
   * The provider defined at a configuration path. A name without dots resolves under {@code
   * akka.javasdk.decision}, so {@code fromConfig("system-one")} reads {@code
   * akka.javasdk.decision.system-one}.
   *
   * <p>The configuration section must define every key of a provider section. Inherit the defaults
   * instead of repeating them:
   *
   * <pre>{@code
   * escalation-model = ${akka.javasdk.decision.system-one}
   * escalation-model.model-name = "jev-1.14.0"
   * }</pre>
   */
  static FromConfig fromConfig(String configPath) {
    return new FromConfig(configPath);
  }

  /**
   * A provider defined in configuration.
   *
   * @param configPath the configuration path, empty for {@code
   *     akka.javasdk.decision.model-provider}
   * @param additionalModelRequestHeaders extra HTTP headers, added to the headers of the provider
   *     that {@code configPath} resolves to
   */
  record FromConfig(String configPath, List<HttpHeader> additionalModelRequestHeaders)
      implements DecisionModelProvider {

    public FromConfig {
      Objects.requireNonNull(configPath, "configPath");
      additionalModelRequestHeaders =
          List.copyOf(
              Objects.requireNonNull(
                  additionalModelRequestHeaders, "additionalModelRequestHeaders"));
    }

    public FromConfig(String configPath) {
      this(configPath, List.of());
    }

    public FromConfig withAdditionalModelRequestHeaders(
        List<HttpHeader> additionalModelRequestHeaders) {
      return new FromConfig(configPath, additionalModelRequestHeaders);
    }
  }

  /**
   * A SystemOne provider with the defaults of {@code akka.javasdk.decision.system-one}. Set the API
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
   * @param apiKey the API key, sent as a bearer token. A call without an API key fails.
   * @param modelName the model name, for example {@code jev-latest}. Pin a model version once you
   *     have tuned your thresholds, because an alias such as {@code jev-latest} can move to a new
   *     version.
   * @param baseUrl the base URL of the API
   * @param connectionTimeout fail the request when connecting takes longer than this
   * @param responseTimeout fail the request when the answer takes longer than this. The timeout
   *     applies to each attempt.
   * @param maxRetries if the request fails with a rate limit, a server error, a timeout or a
   *     connection failure, retry this many times
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
      implements DecisionModelProvider {

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
  non-sealed interface Custom extends DecisionModelProvider {

    /**
     * Answer the request. The returned decision must answer every question with an answer that
     * matches the type, options and levels of the question. The client checks the answers and fails
     * the call with {@link akka.javasdk.agent.ModelException} when one is missing or does not
     * match. Answers for questions that the request did not ask are dropped.
     *
     * <p>The client calls this method on the SDK executor and waits for the result, so it may
     * block. The client applies no timeout, retries or tracing to the call. An exception thrown
     * here fails the request with that exception, unchanged.
     */
    Decision decide(DecisionRequest request);
  }

  /**
   * A request as given to a {@link Custom} provider.
   *
   * @param state the state as passed to {@link DecisionModelClient#state(Object)}
   * @param questions the questions in request order
   */
  record DecisionRequest(Object state, List<Question<?>> questions) {
    public DecisionRequest {
      Objects.requireNonNull(state, "state");
      questions = List.copyOf(Objects.requireNonNull(questions, "questions"));
    }
  }
}
