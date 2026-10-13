/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

/**
 * The model configuration used for an interaction.
 *
 * <p>The structured fields cover the settings the SDK recognises; any additional provider-specific
 * entries are available through {@link InteractionMetadata#modelConfigMap()}.
 *
 * @param providerName the model provider (for example {@code openai}, {@code anthropic})
 * @param modelName the model name as configured
 * @param baseUrl the base URL the model was called through, or empty if not set
 * @param temperature the sampling temperature
 * @param topP the nucleus-sampling probability mass
 * @param topK the top-k sampling cutoff
 * @param maxTokens the maximum number of tokens the model was allowed to produce
 */
public record ModelConfig(
    String providerName,
    String modelName,
    String baseUrl,
    double temperature,
    double topP,
    int topK,
    int maxTokens) {}
