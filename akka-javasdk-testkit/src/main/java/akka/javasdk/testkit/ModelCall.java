/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit;

import java.time.Duration;
import java.util.List;

/**
 * One model call made while answering a turn.
 *
 * @param model the model name, as sent in the request
 * @param provider the provider name
 * @param finishReasons why the model stopped, as the provider reports it
 * @param inputTokens tokens sent, as the provider reports them; zero when it reports none
 * @param outputTokens tokens received; zero when the provider reports none
 * @param duration the duration of the call
 * @param inputMessages the messages sent, as the runtime renders them; empty when not recorded
 * @param outputMessages the messages received; empty when not recorded
 */
public record ModelCall(
    String model,
    String provider,
    List<String> finishReasons,
    long inputTokens,
    long outputTokens,
    Duration duration,
    String inputMessages,
    String outputMessages) {

  public ModelCall {
    model = model == null ? "" : model;
    provider = provider == null ? "" : provider;
    finishReasons = finishReasons == null ? List.of() : List.copyOf(finishReasons);
    duration = duration == null ? Duration.ZERO : duration;
    inputMessages = inputMessages == null ? "" : inputMessages;
    outputMessages = outputMessages == null ? "" : outputMessages;
  }

  public long totalTokens() {
    return inputTokens + outputTokens;
  }
}
