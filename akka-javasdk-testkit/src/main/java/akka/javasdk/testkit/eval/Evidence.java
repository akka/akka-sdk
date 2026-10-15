/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.testkit.eval;

import akka.javasdk.ledger.GuardrailDecisionRecord;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ToolCall;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * What one attempt left in the ledger: the interaction the agent recorded and the guardrail
 * decisions made in it.
 *
 * @param input the command sent to the agent, as text. A String command is used as is, any other
 *     command is rendered as JSON
 * @param interaction the recorded interaction; empty when the agent call failed before the runtime
 *     recorded one
 * @param guardrails the guardrail decisions made in the interaction, newest first
 */
public record Evidence(
    String input,
    Optional<InteractionRecord> interaction,
    List<GuardrailDecisionRecord> guardrails) {

  public Evidence {
    if (input == null) throw new IllegalArgumentException("input required");
    if (interaction == null) throw new IllegalArgumentException("interaction required");
    if (guardrails == null) throw new IllegalArgumentException("guardrails required");
    guardrails = List.copyOf(guardrails);
  }

  /** An attempt that left nothing in the ledger. */
  public static Evidence none(String input) {
    return new Evidence(input, Optional.empty(), List.of());
  }

  /** The agent's reply as recorded; empty when there is no interaction or the agent failed. */
  public String reply() {
    return interaction.map(InteractionRecord::finalResponseText).orElse("");
  }

  /** Why the interaction failed; empty when it did not fail or there is no interaction. */
  public Optional<String> failure() {
    return interaction.flatMap(InteractionRecord::failureSummary);
  }

  /** Whether the interaction failed. False when there is no interaction. */
  public boolean failed() {
    return interaction.map(InteractionRecord::failed).orElse(false);
  }

  /** The tool calls the model requested, in call order. */
  public List<ToolCall> toolCalls() {
    return interaction.map(InteractionRecord::toolCalls).orElse(List.of());
  }

  /** How many model responses the interaction holds. */
  public int modelResponses() {
    return interaction.map(record -> record.modelResponses().size()).orElse(0);
  }

  /** Input tokens summed over the model calls. */
  public long inputTokens() {
    return interaction.map(InteractionRecord::totalInputTokens).orElse(0);
  }

  /** Output tokens summed over the model calls. */
  public long outputTokens() {
    return interaction.map(InteractionRecord::totalOutputTokens).orElse(0);
  }

  /** From the start of the model call to its end, as recorded. Zero without an interaction. */
  public Duration latency() {
    return interaction
        .map(
            record ->
                Duration.between(
                    record.metadata().callStartedAt(), record.metadata().callFinishedAt()))
        .orElse(Duration.ZERO);
  }

  /** Whether a guardrail denied content in the interaction. */
  public boolean denied() {
    return guardrails.stream().anyMatch(g -> g.decision() == GuardrailDecisionRecord.Decision.DENY);
  }
}
