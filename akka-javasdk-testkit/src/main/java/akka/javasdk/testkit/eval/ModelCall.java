/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import java.time.Duration;
import java.util.List;

/**
 * One round trip to the model while answering a turn, as evidence.
 *
 * @param model the model asked, as named in the request
 * @param provider the provider it was asked through
 * @param finishReasons why the model stopped, as the provider reports it; a tool round says so here
 * @param inputTokens tokens sent, as the provider reports them, zero when it reports none
 * @param outputTokens tokens received, likewise
 * @param duration how long the round trip took
 * @param inputMessages the messages sent, as the runtime renders them; empty when not recorded
 * @param outputMessages the messages received, likewise
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
