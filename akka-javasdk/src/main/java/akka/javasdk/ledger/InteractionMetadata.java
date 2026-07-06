/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.ledger;

import java.time.Instant;
import java.util.Map;

/**
 * Metadata about the model call(s) of an interaction.
 *
 * @param modelConfig the structured model configuration used for the interaction
 * @param modelConfigMap the raw configuration entries, including any keys not represented in the
 *     structured {@link ModelConfig}
 * @param callStartedAt when the model call started
 * @param callFinishedAt when the model call finished
 * @param finishReason why the model call finished
 */
public record InteractionMetadata(
    ModelConfig modelConfig,
    Map<String, String> modelConfigMap,
    Instant callStartedAt,
    Instant callFinishedAt,
    FinishReason finishReason) {

  /** Why a model call finished. */
  public enum FinishReason {
    /** The finish reason was not reported. */
    UNSPECIFIED,
    /** The model stopped at a natural stopping point. */
    STOP,
    /** The model stopped because it reached the maximum number of tokens. */
    LENGTH
  }
}
