/*
 * Copyright (C) 2021-2026 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.javasdk.impl.evaluation;

import akka.annotation.InternalApi;
import akka.javasdk.agent.MessageContent;
import akka.javasdk.evaluation.Interaction;
import akka.javasdk.ledger.Failure;
import akka.javasdk.ledger.InteractionMetadata.FinishReason;
import akka.javasdk.ledger.InteractionRecord;
import akka.javasdk.ledger.ModelConfig;
import akka.javasdk.ledger.ModelResponse;
import akka.javasdk.ledger.TaskContext;
import akka.javasdk.ledger.ToolCallResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * INTERNAL API
 *
 * <p>Adapts a ledger {@link InteractionRecord} to the subject content interface {@link
 * Interaction}. The ledger supplies every field, so nothing here reports absent.
 */
@InternalApi
public final class InteractionRecordAdapter implements Interaction {

  private final InteractionRecord record;

  public InteractionRecordAdapter(InteractionRecord record) {
    this.record = record;
  }

  @Override
  public String interactionId() {
    return record.interactionId();
  }

  @Override
  public String systemMessage() {
    return record.systemMessage();
  }

  @Override
  public List<MessageContent> inputMessage() {
    return record.inputMessage();
  }

  @Override
  public List<ModelResponse> modelResponses() {
    return record.modelResponses();
  }

  @Override
  public List<ToolCallResponse> toolCallResponses() {
    return record.toolCallResponses();
  }

  @Override
  public Optional<Failure> failure() {
    return record.failure();
  }

  @Override
  public Optional<String> agentComponentId() {
    return Optional.of(record.agentComponentId());
  }

  @Override
  public Optional<String> sessionId() {
    return Optional.of(record.sessionId());
  }

  @Override
  public Optional<String> flowId() {
    return record.flowId();
  }

  @Override
  public Optional<TaskContext> taskContext() {
    return record.taskContext();
  }

  @Override
  public Optional<Instant> callStartedAt() {
    return Optional.ofNullable(record.metadata().callStartedAt());
  }

  @Override
  public Optional<Instant> callFinishedAt() {
    return Optional.ofNullable(record.metadata().callFinishedAt());
  }

  @Override
  public Optional<FinishReason> finishReason() {
    return Optional.ofNullable(record.metadata().finishReason());
  }

  @Override
  public Optional<ModelConfig> modelConfig() {
    return Optional.ofNullable(record.metadata().modelConfig());
  }

  @Override
  public OptionalInt totalInputTokens() {
    return OptionalInt.of(record.totalInputTokens());
  }

  @Override
  public OptionalInt totalOutputTokens() {
    return OptionalInt.of(record.totalOutputTokens());
  }
}
